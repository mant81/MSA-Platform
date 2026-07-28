package com.msa.member.client.internal;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "hr-employee-client", url = "${app.hr-service-url:http://localhost:8084}")
public interface HrEmployeeFeignClient {

    @GetMapping("/hr/employees/{employeeNo}")
    Map<String, Object> selectByEmployeeNo(@PathVariable String employeeNo);
}
