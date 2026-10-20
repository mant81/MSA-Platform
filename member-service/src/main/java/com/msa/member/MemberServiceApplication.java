package com.msa.member;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;
import com.msa.core.security.ServletSecurityConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Import(ServletSecurityConfiguration.class)
@SpringBootApplication
@EnableFeignClients(basePackages = "com.msa.member.client.internal")
public class MemberServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }
}
