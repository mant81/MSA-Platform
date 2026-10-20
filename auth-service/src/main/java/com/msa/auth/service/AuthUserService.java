package com.msa.auth.service;

import com.msa.auth.mapper.AuthUserMapper;
import com.msa.auth.vo.AuthUserVo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthUserService {
    private final AuthUserMapper mapper;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());

    public AuthUserService(AuthUserMapper mapper) { this.mapper = mapper; }
    public List<AuthUserVo> selectAll() { return mapper.selectAll(); }
    public AuthUserVo selectByUserId(String userId) { return mapper.selectByUserId(userId); }

    @Transactional
    public AuthUserVo insert(String userId, String name, String password) {
        return insertUser(userId, name, password, "USER");
    }

    @Transactional
    public void insertAdministratorIfAbsent(String userId, String password) {
        if (mapper.selectAll().stream().anyMatch(user -> "ADMIN".equals(user.getRole()))) { return; }
        if (mapper.selectByUserId(userId) != null) {
            throw new IllegalStateException("Bootstrap identity already exists; existing users are never promoted automatically");
        }
        if (!userId.matches("[A-Za-z0-9._-]{3,50}")) { throw new IllegalArgumentException("Invalid bootstrap user ID"); }
        insertUser(userId, "Administrator", password, "ADMIN");
    }

    private AuthUserVo insertUser(String userId, String name, String password, String role) {
        if (password == null || password.length() < 12 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password length is invalid");
        }
        AuthUserVo user = new AuthUserVo();
        user.setUserId(userId);user.setUserName(name);user.setPasswordHash(passwords.encode(password));
        user.setStatus("ACTIVE");user.setRole(role);mapper.insert(user);
        return user;
    }

    public AuthUserVo selectByUserIdAndPassword(String userId, String password) {
        AuthUserVo user = mapper.selectByUserId(userId);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean encoded = hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$"));
        String supplied = password == null ? "" : password;
        boolean lengthValid = supplied.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matched = passwords.matches(lengthValid ? supplied : "", encoded ? hash : dummyHash);
        return user != null && encoded && lengthValid && matched && "ACTIVE".equals(user.getStatus()) ? user : null;
    }
}
