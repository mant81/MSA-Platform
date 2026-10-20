package com.msa.hr;

import com.msa.core.security.ServletSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({ServletSecurityConfiguration.class, com.msa.core.security.ApiExceptionHandler.class})
@SpringBootApplication
public class HrServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(HrServiceApplication.class, args);
  }
}
