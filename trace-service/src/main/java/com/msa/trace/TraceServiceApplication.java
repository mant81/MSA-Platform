package com.msa.trace;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;
import com.msa.core.security.ServletSecurityConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Import(ServletSecurityConfiguration.class)
@SpringBootApplication
public class TraceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraceServiceApplication.class, args);
    }
}
