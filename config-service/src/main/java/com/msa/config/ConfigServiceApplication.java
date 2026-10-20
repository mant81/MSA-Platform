package com.msa.config;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;
import com.msa.core.security.ServletSecurityConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Import(ServletSecurityConfiguration.class)
@SpringBootApplication
public class ConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
