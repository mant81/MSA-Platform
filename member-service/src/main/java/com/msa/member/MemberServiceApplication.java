package com.msa.member;

import com.msa.core.security.ServletSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@Import({ServletSecurityConfiguration.class, com.msa.core.security.ApiExceptionHandler.class})
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
@EnableFeignClients(basePackages = "com.msa.member.client.internal")
public class MemberServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(MemberServiceApplication.class, args);
  }
}
