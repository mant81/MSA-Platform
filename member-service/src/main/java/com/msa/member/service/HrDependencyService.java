package com.msa.member.service;

import com.msa.member.client.internal.HrEmployeeFeignClient;
import io.github.resilience4j.circuitbreaker.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HrDependencyService {
  private final HrEmployeeFeignClient client;
  private final CircuitBreaker circuit;

  public HrDependencyService(HrEmployeeFeignClient client) {
    this.client = client;
    circuit =
        CircuitBreaker.of(
            "hr",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
  }

  public Map<String, Object> selectEmployee(String number, String trace) {
    return circuit.executeSupplier(() -> client.selectByEmployeeNo(number, trace));
  }

  public String selectState() {
    return circuit.getState().name();
  }
}
