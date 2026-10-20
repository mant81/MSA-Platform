package com.msa.member.service;

import com.msa.member.vo.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Durable claim is committed separately; all business effects and the response commit together. */
@Service
public class SignupRequestService {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final MemberService members;
  private final JsonMapper json = JsonMapper.builder().build();

  public SignupRequestService(
      JdbcTemplate jdbc,
      org.springframework.transaction.PlatformTransactionManager manager,
      MemberService members) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(manager);
    this.members = members;
  }

  private String digest(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public MemberSignupVo insert(MemberVo vo, String traceId, String owner, String suppliedKey) {
    if (vo.getMemberNo() == null
        || !vo.getMemberNo().matches("[A-Za-z0-9._-]{1,50}")
        || vo.getMemberName() == null
        || vo.getMemberName().isBlank()
        || vo.getMemberName().length() > 100
        || (vo.getStatus() != null && vo.getStatus().length() > 20)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid member input");
    }
    String key = suppliedKey == null ? vo.getMemberNo() : suppliedKey;
    if (!key.matches("[A-Za-z0-9._:-]{1,100}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
    String requestKey = digest(owner + "\n" + key);
    String fingerprint =
        digest(
            json.writeValueAsString(
                java.util.List.of(
                    vo.getMemberNo(),
                    vo.getMemberName(),
                    vo.getStatus() == null ? "ACTIVE" : vo.getStatus())));
    try {
      tx.executeWithoutResult(
          status ->
              jdbc.update(
                  "insert into signup_request(request_key,fingerprint,status,created_at,updated_at)"
                      + " values (?,?,'RUNNING',?,?)",
                  requestKey,
                  fingerprint,
                  OffsetDateTime.now(),
                  OffsetDateTime.now()));
    } catch (DuplicateKeyException duplicate) {
      var row =
          jdbc.queryForMap(
              "select fingerprint,status,response_json from signup_request where request_key=?",
              requestKey);
      if (!fingerprint.equals(row.get("FINGERPRINT"))
          && !fingerprint.equals(row.get("fingerprint")))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key payload differs");
      Object result = row.getOrDefault("RESPONSE_JSON", row.get("response_json"));
      if (result == null)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Request running or requires recovery");
      return json.readValue(result.toString(), MemberSignupVo.class);
    }
    try {
      return tx.execute(
          status -> {
            // Lock claim: watchdog cannot mark a live transaction abandoned.
            jdbc.queryForMap(
                "select request_key from signup_request where request_key=? for update",
                requestKey);
            MemberSignupVo response = members.insertSignup(vo, traceId);
            jdbc.update(
                "update signup_request set status='COMPLETED',response_json=?,updated_at=? where"
                    + " request_key=?",
                json.writeValueAsString(response),
                OffsetDateTime.now(),
                requestKey);
            return response;
          });
    } catch (RuntimeException ex) {
      tx.executeWithoutResult(
          status ->
              jdbc.update(
                  "update signup_request set status='RECOVERY_REQUIRED',updated_at=? where"
                      + " request_key=? and status='RUNNING' and response_json is null",
                  OffsetDateTime.now(),
                  requestKey));
      if (ex instanceof DuplicateKeyException)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Member already exists");
      throw ex;
    }
  }

  @org.springframework.scheduling.annotation.Scheduled(
      fixedDelayString = "${app.signup-recovery-ms:60000}")
  public void recover() {
    jdbc.update(
        "update signup_request set status='RECOVERY_REQUIRED',updated_at=? where status='RUNNING'"
            + " and updated_at < ?",
        OffsetDateTime.now(),
        OffsetDateTime.now().minusMinutes(5));
  }
}
