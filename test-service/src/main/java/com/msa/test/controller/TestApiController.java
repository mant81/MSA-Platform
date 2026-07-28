package com.msa.test.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api")
public class TestApiController {
    private final RestClient gatewayClient;

    public TestApiController(@Value("${app.gateway-url:http://localhost:8080}") String gatewayUrl) {
        this.gatewayClient = RestClient.builder().baseUrl(gatewayUrl).build();
    }

    @GetMapping("/gateway/{path}")
    public Object gateway(@PathVariable String path) {
        return gatewayClient.get()
                .uri("/" + path)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/gateway/auth-login")
    public Object gatewayAuthLogin() {
        return gatewayClient.post()
                .uri("/auth/login")
                .body(Map.of("authType", "PASSWORD", "userId", "user01", "password", "1234"))
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/hr/employees/{employeeNo}")
    public Object hrEmployee(@PathVariable String employeeNo) {
        return gatewayClient.get()
                .uri("/hr/employees/{employeeNo}", employeeNo)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/member/members")
    public Object memberSelectAll() {
        return gatewayClient.get().uri("/members").retrieve().body(Object.class);
    }

    @GetMapping("/member/members/{memberNo}")
    public Object memberSelectOne(@PathVariable String memberNo) {
        return gatewayClient.get().uri("/members/{memberNo}", memberNo).retrieve().body(Object.class);
    }

    @PostMapping("/member/members")
    public Object memberInsert(@RequestBody Map<String, Object> body) {
        return gatewayClient.post().uri("/members").body(body).retrieve().body(Object.class);
    }

    @PostMapping("/member/members/signup")
    public Object memberSignup(@RequestBody Map<String, Object> body) {
        return gatewayClient.post().uri("/members/signup").body(body).retrieve().body(Object.class);
    }

    @GetMapping("/member/members/processes")
    public Object memberProcesses() {
        return gatewayClient.get().uri("/members/processes").retrieve().body(Object.class);
    }

    @GetMapping("/member/members/processes/{processId}/timeline")
    public Object memberTimeline(@PathVariable String processId) {
        return gatewayClient.get().uri("/members/processes/{processId}/timeline", processId).retrieve().body(Object.class);
    }

    @PostMapping("/auth/signup")
    public Object authSignup(@RequestBody Map<String, Object> body) {
        return gatewayClient.post().uri("/auth/signup").body(body).retrieve().body(Object.class);
    }

    @PostMapping("/auth/login")
    public Object authLogin(@RequestBody Map<String, Object> body) {
        return gatewayClient.post().uri("/auth/login").body(body).retrieve().body(Object.class);
    }

    @GetMapping("/config/configs")
    public Object configSelectAll() {
        return gatewayClient.get().uri("/configs").retrieve().body(Object.class);
    }

    @PostMapping("/config/configs")
    public Object configInsert(@RequestBody Map<String, Object> body) {
        return gatewayClient.post().uri("/configs").body(body).retrieve().body(Object.class);
    }

    @GetMapping("/trace/trace-events")
    public Object traceSelectAll() {
        return gatewayClient.get().uri("/trace-events").retrieve().body(Object.class);
    }

    @GetMapping("/trace/trace-events/trace-id/{traceId}")
    public Object traceByTraceId(@PathVariable String traceId) {
        return gatewayClient.get().uri("/trace-events/trace-id/{traceId}", traceId).retrieve().body(Object.class);
    }

    @GetMapping("/trace/trace-events/process-id/{processId}")
    public Object traceByProcessId(@PathVariable String processId) {
        return gatewayClient.get().uri("/trace-events/process-id/{processId}", processId).retrieve().body(Object.class);
    }

    @GetMapping("/trace/trace-events/status/{status}")
    public Object traceByStatus(@PathVariable String status) {
        return gatewayClient.get().uri("/trace-events/status/{status}", status).retrieve().body(Object.class);
    }

    @GetMapping("/trace/trace-events/process-id/{processId}/trace-id/{traceId}")
    public Object traceByProcessIdAndTraceId(@PathVariable String processId, @PathVariable String traceId) {
        return gatewayClient.get()
                .uri("/trace-events/process-id/{processId}/trace-id/{traceId}", processId, traceId)
                .retrieve()
                .body(Object.class);
    }
}
