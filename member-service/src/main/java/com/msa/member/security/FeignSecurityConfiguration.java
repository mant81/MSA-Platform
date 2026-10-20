package com.msa.member.security;

import com.msa.core.security.ServiceCredentials;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration(proxyBeanMethods = false)
public class FeignSecurityConfiguration {
  @Bean
  public RequestInterceptor serviceAuthentication(ServiceCredentials credentials) {
    return template -> {
      template.header("X-Service-Id", credentials.id());
      template.header(
          "X-Service-Token",
          credentials.tokenFor(
              "hr-employee-client".equals(template.feignTarget().name())
                  ? "hr-service"
                  : "auth-service"));
      if (RequestContextHolder.getRequestAttributes()
          instanceof ServletRequestAttributes attributes) {
        if (attributes.getRequest().getAttribute("traceContext")
            instanceof com.msa.core.TraceContext context) {
          String span = (String) attributes.getRequest().getAttribute("activeStepSpanId");
          template.header(
              "traceparent",
              span == null
                  ? context.header()
                  : new com.msa.core.TraceContext(
                          context.traceId(),
                          span,
                          context.spanId(),
                          context.legacyTraceId(),
                          context.flags())
                      .header());
          Object process = attributes.getRequest().getAttribute("processId");
          if (process != null) template.header("X-Process-Id", process.toString());
        }
        String authorization = attributes.getRequest().getHeader("Authorization");
        if (authorization != null) {
          template.header("Authorization", authorization);
        }
      }
    };
  }
}
