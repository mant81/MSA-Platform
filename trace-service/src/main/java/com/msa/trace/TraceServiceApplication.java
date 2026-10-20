package com.msa.trace;

import com.msa.core.security.ServletSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({ServletSecurityConfiguration.class, com.msa.core.security.ApiExceptionHandler.class})
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class TraceServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(TraceServiceApplication.class, args);
  }
}
