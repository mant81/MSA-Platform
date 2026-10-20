package com.msa.auth;

import com.msa.auth.service.AuthUserService;
import com.msa.auth.service.RefreshTokenService;
import com.msa.auth.auth.dto.AuthLoginResponseVo;
import com.msa.core.security.SecurityFixture;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthSecurityIntegrationTest {
    private static final SecurityFixture SECURITY = new SecurityFixture("auth-service");
    private static final String PASSWORD = "Test-password-12345";
    @Autowired AuthUserService users;
    @Autowired RefreshTokenService sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder decoder;

    @DynamicPropertySource
    static void security(DynamicPropertyRegistry registry) {
        SECURITY.properties().forEach((key, value) -> registry.add(key, () -> value));
    }
    @AfterAll
    static void cleanup() throws Exception { SECURITY.close(); }
    private String user() {
        String id = "usr" + UUID.randomUUID().toString().replace("-", "");
        users.insert(id, "Fixture", PASSWORD);return id;
    }
    private AuthLoginResponseVo issue() { return sessions.issue(users.selectByUserId(user())); }

    @Test
    void passwordIsHashedAndNeverSerialized() {
        String id = user();
        String hash = jdbc.queryForObject("select password_hash from auth_user where user_id=?", String.class, id);
        assertTrue(hash.startsWith("$2"));assertFalse(hash.equals(PASSWORD));
        String json = JsonMapper.builder().build().writeValueAsString(users.selectByUserId(id));
        assertFalse(json.contains("passwordHash"));assertFalse(json.contains(hash));assertFalse(json.contains(PASSWORD));
        assertNotNull(users.selectByUserIdAndPassword(id, PASSWORD));
        assertNull(users.selectByUserIdAndPassword(id, "incorrect"));
    }

    @Test
    void legacyPlaintextRequiresReset() {
        String id = user();jdbc.update("update auth_user set password_hash=? where user_id=?", PASSWORD, id);
        assertNull(users.selectByUserIdAndPassword(id, PASSWORD));
    }

    @Test
    void inactiveUserCannotLogin() {
        String id = user();jdbc.update("update auth_user set status='DISABLED' where user_id=?", id);
        assertNull(users.selectByUserIdAndPassword(id, PASSWORD));
    }

    @Test
    void refreshUsesHashesAndReuseCommitsFamilyRevocation() {
        var original = issue();
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_session where token_hash=?", Integer.class, original.getRefreshToken()));
        var next = sessions.rotate(original.getRefreshToken());assertTrue(next.isSuccess());
        assertFalse(original.getRefreshToken().equals(next.getRefreshToken()));
        assertFalse(sessions.rotate(original.getRefreshToken()).isSuccess());
        assertFalse(sessions.rotate(next.getRefreshToken()).isSuccess());
        assertTrue(jdbc.queryForObject("select count(*) from auth_security_event where event_type='REFRESH_REUSED'", Integer.class) > 0);
    }

    @Test
    void logoutRevokesFamily() {
        var original = issue();var next = sessions.rotate(original.getRefreshToken());
        sessions.logout(original.getRefreshToken());
        assertFalse(sessions.rotate(next.getRefreshToken()).isSuccess());
    }

    @Test
    void expiredRefreshIsRejected() {
        var original = issue();
        String family = decoder.decode(original.getAccessToken()).getClaimAsString("sid");
        jdbc.update("update refresh_session set expires_at=dateadd('DAY',-1,current_timestamp) where family_id=?", family);
        assertFalse(sessions.rotate(original.getRefreshToken()).isSuccess());
    }

    @Test
    void concurrentRotationHasOneWinnerAndReuseRevokesDescendants() throws Exception {
        var original = issue();var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await();return sessions.rotate(original.getRefreshToken()); });
            var b = executor.submit(() -> { start.await();return sessions.rotate(original.getRefreshToken()); });
            start.countDown();var x = a.get();var y = b.get();
            assertTrue(x.isSuccess() ^ y.isSuccess());
            assertFalse(sessions.rotate((x.isSuccess() ? x : y).getRefreshToken()).isSuccess());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"current", "next"})
    void trustedRotationKeysAreAccepted(String kid) throws Exception {
        assertEquals("fixture-user", decoder.decode(SECURITY.sign(Map.of(), kid, false)).getSubject());
    }

    @Test
    void badSignatureAndUnknownKidAreRejected() {
        assertThrows(JwtException.class, () -> decoder.decode(SECURITY.sign(Map.of(), "current", true)));
        assertThrows(JwtException.class, () -> decoder.decode(SECURITY.sign(Map.of(), "unknown", false)));
    }

    @Test
    void invalidClaimsAreRejected() {
        for (var claims : List.of(Map.<String,Object>of("iss", "https://other.invalid"), Map.<String,Object>of("aud", "other"),
                Map.<String,Object>of("token_use", "refresh"), Map.<String,Object>of("roles", "ADMIN"),
                Map.<String,Object>of("exp", Date.from(Instant.now().minusSeconds(120))),
                Map.<String,Object>of("nbf", Date.from(Instant.now().plusSeconds(120))))) {
            assertThrows(JwtException.class, () -> decoder.decode(SECURITY.sign(claims, "current", false)));
        }
    }
}
