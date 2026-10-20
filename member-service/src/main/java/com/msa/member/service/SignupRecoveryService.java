package com.msa.member.service;

import com.msa.member.mapper.SignupRecoveryMapper;
import com.msa.member.vo.SignupRecoveryVo;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SignupRecoveryService {
  private final SignupRecoveryMapper mapper;
  private final TransactionTemplate tx;

  public SignupRecoveryService(
      SignupRecoveryMapper mapper,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.tx = new TransactionTemplate(manager);
  }

  private void validateKey(String key) {
    if (key == null || !key.matches("[0-9a-f]{64}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request key");
  }

  public List<SignupRecoveryVo> selectRequests(String cursor) {
    if (!cursor.isEmpty()) validateKey(cursor);
    return mapper.selectRequests(cursor);
  }

  public List<Map<String, Object>> selectAudit(String key) {
    validateKey(key);
    return mapper.selectAudit(key);
  }

  public SignupRecoveryVo allowRetry(String key, String reason, String actor, String trace) {
    validateKey(key);
    if (!Set.of("DB_ROLLBACK_CONFIRMED", "PROCESS_CRASH_CONFIRMED")
        .contains(Objects.toString(reason, "")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recovery reason");
    return tx.execute(
        status -> {
          var request = mapper.selectLocked(key);
          if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
          if (!"RECOVERY_REQUIRED".equals(request.getStatus())
              || request.isHasResponse()
              || request.getOwnerId() == null
              || request.getMemberNo() == null
              || request.getRetryCount() >= 3
              || mapper.selectEffects(request.getMemberNo()) > 0)
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Request cannot be safely retried");
          var now = OffsetDateTime.now();
          if (mapper.updateRetryAllowed(key, now) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT);
          mapper.insertAudit(
              key, actor, "RETRY_ALLOWED", reason, trace, now, UUID.randomUUID().toString());
          return mapper.selectLocked(key);
        });
  }
}
