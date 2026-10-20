package com.msa.core.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration(proxyBeanMethods = false)
public class JwtSecurityConfiguration {
    @Bean
    public ServiceCredentials serviceCredentials(@Value("${MSA_SERVICE_CREDENTIALS_FILE}") String file,
            @Value("${spring.application.name}") String service) throws Exception {
        ServiceCredentials credentials = ServiceCredentials.load(Path.of(file));
        if (!service.equals(credentials.id())) { throw new IllegalArgumentException("Service identity does not match application"); }
        return credentials;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${MSA_JWT_PUBLIC_KEY_DIRECTORY}") String directory,
            @Value("${MSA_JWT_ISSUER:https://msa.local/auth}") String issuer,
            @Value("${MSA_JWT_AUDIENCE:msa-platform}") String audience) throws Exception {
        List<JWK> keys = new ArrayList<>();
        try (var paths = Files.list(Path.of(directory))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".pem")).toList()) {
                String pem = Files.readString(path).replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
                var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
                if (key.getModulus().bitLength() < 2048) { throw new IllegalArgumentException("RSA public keys must be at least 2048 bits"); }
                String kid = path.getFileName().toString().replaceFirst("\\.pem$", "");
                keys.add(new RSAKey.Builder(key).keyID(kid).build());
            }
        }
        if (keys.isEmpty()) { throw new IllegalArgumentException("At least one public key is required"); }
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
                new ImmutableJWKSet<>(new JWKSet(keys))));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)), new JwtIssuerValidator(issuer), jwt -> {
                    Object roles = jwt.getClaims().get("roles");
                    boolean roleValid = roles instanceof List<?> list && !list.isEmpty()
                            && list.stream().allMatch(role -> List.of("USER", "OPERATIONS", "ADMIN").contains(role));
                    boolean valid = roleValid && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null && jwt.getNotBefore() != null
                            && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                            && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() <= 3600
                            && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))
                            && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                            && jwt.getAudience().contains(audience) && "access".equals(jwt.getClaimAsString("token_use"))
                            && jwt.getHeaders().get("kid") instanceof String kid && !kid.isBlank();
                    return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Required access-token claims are invalid", null));
                }));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
