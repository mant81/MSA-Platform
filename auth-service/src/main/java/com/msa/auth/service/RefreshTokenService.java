package com.msa.auth.service;

import com.msa.auth.auth.dto.AuthLoginResponseVo;
import com.msa.auth.auth.token.JwtTokenService;
import com.msa.auth.mapper.AuthRefreshMapper;
import com.msa.auth.vo.AuthUserVo;
import com.msa.auth.vo.RefreshFamilyVo;
import com.msa.auth.vo.RefreshSessionVo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private final AuthRefreshMapper mapper;
    private final AuthUserService users;
    private final JwtTokenService jwt;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(AuthRefreshMapper mapper, AuthUserService users, JwtTokenService jwt) {
        this.mapper = mapper;this.users = users;this.jwt = jwt;
    }

    @Transactional
    public AuthLoginResponseVo register(String userId, String name, String password) {
        return issue(users.insert(userId, name, password));
    }

    @Transactional
    public AuthLoginResponseVo issue(AuthUserVo user) {
        RefreshFamilyVo family = new RefreshFamilyVo();
        family.setFamilyId(UUID.randomUUID().toString());family.setUserId(user.getUserId());
        family.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));mapper.insertFamily(family);
        audit(user.getUserId(), family.getFamilyId(), "SESSION_CREATED");
        return tokens(user, family);
    }

    @Transactional
    public AuthLoginResponseVo rotate(String raw) {
        RefreshSessionVo session = select(raw);
        if (session == null) { audit(null, null, "REFRESH_INVALID");return invalid(); }
        RefreshFamilyVo family = mapper.selectFamilyForUpdate(session.getFamilyId());
        session = mapper.selectSession(hash(raw));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (session.getUsedAt() != null) {
            revoke(family, now);audit(family.getUserId(), family.getFamilyId(), "REFRESH_REUSED");
            return invalid(); // Return instead of throwing so the revocation transaction commits.
        }
        if (session.getRevokedAt() != null || family.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now) || !family.getExpiresAt().isAfter(now)) {
            revoke(family, now);audit(family.getUserId(), family.getFamilyId(), "REFRESH_EXPIRED_OR_REVOKED");return invalid();
        }
        AuthUserVo user = users.selectByUserId(family.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus()) || mapper.markUsed(hash(raw), now) != 1) {
            revoke(family, now);audit(family.getUserId(), family.getFamilyId(), "REFRESH_REJECTED");return invalid();
        }
        audit(user.getUserId(), family.getFamilyId(), "REFRESH_ROTATED");
        return tokens(user, family);
    }

    @Transactional
    public void logout(String raw) {
        RefreshSessionVo session = select(raw);
        if (session == null) { audit(null, null, "LOGOUT_UNKNOWN");return; }
        RefreshFamilyVo family = mapper.selectFamilyForUpdate(session.getFamilyId());
        revoke(family, OffsetDateTime.now(ZoneOffset.UTC));audit(family.getUserId(), family.getFamilyId(), "LOGOUT");
    }

    private RefreshSessionVo select(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9_-]{43}") ? mapper.selectSession(hash(raw)) : null;
    }

    private AuthLoginResponseVo tokens(AuthUserVo user, RefreshFamilyVo family) {
        byte[] bytes = new byte[32];random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshSessionVo session = new RefreshSessionVo();session.setTokenHash(hash(raw));
        session.setFamilyId(family.getFamilyId());session.setExpiresAt(family.getExpiresAt());mapper.insertSession(session);
        AuthLoginResponseVo response = new AuthLoginResponseVo();response.setSuccess(true);
        response.setAuthType("PASSWORD");response.setTokenType("Bearer");response.setExpiresIn(jwt.expiresIn());
        response.setAccessToken(jwt.createAccessToken(user.getUserId(), user.getRole(), family.getFamilyId()));
        response.setRefreshToken(raw);response.setMessage("ok");return response;
    }

    private AuthLoginResponseVo invalid() {
        AuthLoginResponseVo response = new AuthLoginResponseVo();response.setSuccess(false);
        response.setCode("REFRESH_INVALID");response.setMessage("Refresh token is invalid");return response;
    }

    private void revoke(RefreshFamilyVo family, OffsetDateTime now) {
        mapper.revokeFamily(family.getFamilyId(), now);mapper.revokeSessions(family.getFamilyId(), now);
    }

    private void audit(String user, String family, String type) {
        mapper.insertAudit(UUID.randomUUID().toString(), user, family, type, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
