package com.msa.hr;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import com.msa.core.security.SecurityFixture;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HrServiceIntegrationTest {
    private static final SecurityFixture SECURITY = new SecurityFixture("hr-service");
    @DynamicPropertySource
    static void security(DynamicPropertyRegistry registry) {
        SECURITY.properties().forEach((key, value) -> registry.add(key, () -> value));
    }
    @AfterAll
    static void cleanup() throws Exception { SECURITY.close(); }

    @Value("${local.server.port}")
    private int port;

    @Test
    void activeEmployeeUsesTheHrResponseContract() throws Exception {
        Map<?, ?> employee = selectEmployee("EMPLOYEE-10001");
        assertEquals(true, employee.get("active"));
        assertEquals("ACTIVE", employee.get("employeeStatus"));
        assertEquals("HR0000", employee.get("hrResultCode"));
    }

    @Test
    void inactiveEmployeeIsRejected() throws Exception {
        Map<?, ?> employee = selectEmployee("EMPLOYEE-10002");
        assertEquals(false, employee.get("active"));
        assertEquals("INACTIVE", employee.get("employeeStatus"));
    }

    @Test
    void missingEmployeeIsRejected() throws Exception {
        Map<?, ?> employee = selectEmployee("DOES-NOT-EXIST");
        assertEquals(false, employee.get("active"));
        assertEquals("NOT_FOUND", employee.get("employeeStatus"));
        assertEquals("HR0404", employee.get("hrResultCode"));
    }

    private Map<?, ?> selectEmployee(String employeeNo) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/hr/employees/" + employeeNo))
                .header("X-Trace-Id", "hr-regression")
                .header("X-Service-Id", "gateway-service").header("X-Service-Token", SecurityFixture.GATEWAY_TOKEN)
                .header("Authorization", "Bearer " + SECURITY.sign(Map.of(), "current", false)).build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("hr-regression", response.headers().firstValue("X-Trace-Id").orElse(null));
        return JsonMapper.builder().build().readValue(response.body(), Map.class);
    }
}
