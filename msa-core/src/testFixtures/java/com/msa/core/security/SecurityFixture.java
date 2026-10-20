package com.msa.core.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Disposable test keys, never used as runtime defaults. */
public class SecurityFixture implements AutoCloseable {
    public static final String GATEWAY_TOKEN = "g".repeat(64);
    private final Path directory;
    private final Map<String, KeyPair> keys;
    private final String service;

    public SecurityFixture(String service) {
        this.service = service;
        try {
            directory = Files.createTempDirectory("msa-security-test-");
            var generator = KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
            keys = Map.of("current", generator.generateKeyPair(), "next", generator.generateKeyPair(), "wrong", generator.generateKeyPair());
            Path publicDirectory = Files.createDirectory(directory.resolve("public"));
            for (String kid : List.of("current", "next")) {
                Files.writeString(publicDirectory.resolve(kid + ".pem"), pem("PUBLIC KEY", keys.get(kid).getPublic().getEncoded()));
            }
            Files.writeString(directory.resolve("private.pem"), pem("PRIVATE KEY", keys.get("current").getPrivate().getEncoded()));
            Files.writeString(directory.resolve("service.properties"), "identity.id=" + service
                    + "\noutbound.trace-service=" + "s".repeat(64) + "\ntrusted.gateway-service=" + GATEWAY_TOKEN + "\n");
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }

    public Map<String, String> properties() {
        return Map.of("MSA_JWT_PUBLIC_KEY_DIRECTORY", directory.resolve("public").toString(),
                "MSA_JWT_PRIVATE_KEY_FILE", directory.resolve("private.pem").toString(), "MSA_JWT_KEY_ID", "current",
                "MSA_SERVICE_CREDENTIALS_FILE", directory.resolve("service.properties").toString(),
                "MSA_BOOTSTRAP_ADMIN_USER", "", "MSA_BOOTSTRAP_ADMIN_PASSWORD", "");
    }

    public String sign(Map<String, Object> overrides, String kid, boolean wrongKey) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("https://msa.local/auth").subject("fixture-user")
                .audience("msa-platform").issueTime(Date.from(now)).notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300))).claim("token_use", "access").claim("roles", List.of("USER"));
        overrides.forEach(claims::claim);
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims.build());
        token.sign(new RSASSASigner(keys.get(wrongKey ? "wrong" : (keys.containsKey(kid) ? kid : "current")).getPrivate()));
        return token.serialize();
    }

    @Override
    public void close() throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
        }
    }
}
