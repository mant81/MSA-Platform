package com.msa.config;

import com.msa.core.security.ServletSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({ServletSecurityConfiguration.class, com.msa.core.security.ApiExceptionHandler.class})
@SpringBootApplication
public class ConfigServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ConfigServiceApplication.class, args);
  }
}
