package com.msa.member.client.internal;

import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "auth-user-client", url = "${app.auth-service-url:http://localhost:8082}")
public interface AuthUserFeignClient {

    @GetMapping("/users")
    List<Map<String, Object>> selectAllAuthUsers();
}
