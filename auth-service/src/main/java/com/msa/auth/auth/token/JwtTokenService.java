package com.msa.auth.auth.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final String kid;
    private final String issuer;
    private final String audience;
    private final long ttl;

    public JwtTokenService(@Value("${MSA_JWT_PRIVATE_KEY_FILE}") String privateFile,
            @Value("${MSA_JWT_PUBLIC_KEY_DIRECTORY}") String publicDirectory,
            @Value("${MSA_JWT_KEY_ID:current}") String kid,
            @Value("${MSA_JWT_ISSUER:https://msa.local/auth}") String issuer,
            @Value("${MSA_JWT_AUDIENCE:msa-platform}") String audience,
            @Value("${MSA_ACCESS_TTL_SECONDS:900}") long ttl) throws Exception {
        if (!kid.matches("[A-Za-z0-9_-]{1,64}") || ttl < 60 || ttl > 3600) {
            throw new IllegalArgumentException("Invalid JWT key ID or access TTL");
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(readPem(privateFile, "PRIVATE KEY")));
        var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(readPem(Path.of(publicDirectory, kid + ".pem").toString(), "PUBLIC KEY")));
        if (publicKey.getModulus().bitLength() < 2048 || !publicKey.getModulus().equals(privateKey.getModulus())
                || !publicKey.getPublicExponent().equals(privateKey.getPublicExponent())) {
            throw new IllegalArgumentException("RSA signing key must match the configured public key");
        }
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
                new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build())));
        this.kid = kid;this.issuer = issuer;this.audience = audience;this.ttl = ttl;
    }

    private byte[] readPem(String file, String type) throws Exception {
        return Base64.getDecoder().decode(Files.readString(Path.of(file))
                .replace("-----BEGIN " + type + "-----", "").replace("-----END " + type + "-----", "").replaceAll("\\s", ""));
    }

    public String createAccessToken(String userId, String role, String familyId) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(userId).audience(List.of(audience))
                .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(ttl)).id(UUID.randomUUID().toString())
                .claim("token_use", "access").claim("roles", List.of(role)).claim("sid", familyId).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build(), claims)).getTokenValue();
    }

    public long expiresIn() { return ttl; }
}
