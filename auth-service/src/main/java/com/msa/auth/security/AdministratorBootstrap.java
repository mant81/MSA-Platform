package com.msa.auth.security;

import com.msa.auth.service.AuthUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdministratorBootstrap implements ApplicationRunner {
    private final AuthUserService users;
    private final String user;
    private final String password;
    public AdministratorBootstrap(AuthUserService users,
            @Value("${MSA_BOOTSTRAP_ADMIN_USER:}") String user,
            @Value("${MSA_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users;this.user = user;this.password = password;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (user.isEmpty() && password.isEmpty()) { return; }
        if (user.isEmpty() || password.isEmpty()) { throw new IllegalArgumentException("Both bootstrap variables are required"); }
        users.insertAdministratorIfAbsent(user, password);
    }
}
