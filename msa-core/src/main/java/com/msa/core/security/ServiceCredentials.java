package com.msa.core.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.http.HttpHeaders;

public record ServiceCredentials(String id, Map<String, String> outbound, Map<String, String> trusted) {
    public ServiceCredentials {
        if (id == null || id.isBlank()) { throw new IllegalArgumentException("Service identity is required"); }
        outbound = Map.copyOf(outbound);trusted = Map.copyOf(trusted);
        if (outbound.values().stream().anyMatch(value -> value.length() < 32)
                || trusted.values().stream().anyMatch(value -> value.length() < 32)) {
            throw new IllegalArgumentException("Service credentials must contain at least 32 characters");
        }
    }

    public static ServiceCredentials load(Path path) throws IOException {
        Properties props = new Properties();
        try (var input = Files.newInputStream(path)) { props.load(input); }
        Map<String, String> outbound = new HashMap<>();Map<String, String> trusted = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("trusted.")) { trusted.put(key.substring(8), props.getProperty(key)); }
            if (key.startsWith("outbound.")) { outbound.put(key.substring(9), props.getProperty(key)); }
        }
        return new ServiceCredentials(props.getProperty("identity.id"), outbound, trusted);
    }

    public boolean accepts(String caller, String supplied) {
        if (caller == null || supplied == null) { return false; }
        String expected = trusted.get(caller);
        return expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    public String tokenFor(String target) {
        String token = outbound.get(target);
        if (token == null) { throw new IllegalArgumentException("No outbound credential is configured for target service"); }
        return token;
    }

    public void apply(HttpHeaders headers, String target) {
        headers.set("X-Service-Id", id);headers.set("X-Service-Token", tokenFor(target));
    }

    @Override
    public String toString() {
        return "ServiceCredentials[id=" + id + ", outboundCount=" + outbound.size() + ", trustedCount=" + trusted.size() + "]";
    }
}
