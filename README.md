# MSA PLATFORM

MSA PLATFORM is a small Spring Boot 3 + Java 21 + Gradle based platform for trace-aware microservices.

## What this platform contains
- `gateway-service`
- `trace-service`
- `member-service`
- `hr-service`
- `auth-service`
- `config-service`
- `test-service`
- `msa-core`

## Test service purpose
- `test-service` is for MSA backend integration testing.
- It is a Thymeleaf-based UI test app, not a business domain service.
- Use it to verify Gateway routing, trace propagation, and service-specific API behavior through Ajax.
- It does not need DB integration.
- Keep it simple and disposable.
- It is organized by service purpose: `member`, `auth`, `config`, `trace`, `gateway`.
- The backend endpoint for test flows is always `gateway-service`.

## Shared rules
- Use `com.msa` as the base package.
- Use `select*` for reads.
- Use `insert` for writes.
- Keep service code simple: `controller`, `service`, `mapper`, `xml`, `vo`.
- Do not add DTOs unless explicitly requested.
- Default database is H2.
- DB type is selected per service by `app.database-type`.

## Trace rules
- `X-Trace-Id` is mandatory on every request and response.
- If a request does not include `X-Trace-Id`, Gateway generates one.
- Gateway forwards the same `X-Trace-Id` downstream.
- Downstream services echo `X-Trace-Id` in the response header.
- All trace events are stored centrally in `trace-service`.

## Call boundary definitions

### Internal call
Internal call means a call between platform services that are owned and deployed as part of this MSA.

Examples:
- `gateway-service -> member-service`
- `gateway-service -> auth-service`
- `member-service -> auth-service`
- `member-service -> config-service`
- `member-service -> hr-service`

### hr-service purpose
- `hr-service` is fully standalone.
- HR response format is independent from other services.
- Other services should only consume the HR response contract through their own mapping.
- HR is used as the employee verification source for member signup trace flow.

Rules:
- Use for service-to-service business calls inside the platform.
- Prefer `Feign Client` for synchronous internal calls.
- Always propagate `X-Trace-Id`.
- Always write trace events to `trace-service`.

### External call
External call means a call to a system outside this platform boundary.

Examples:
- Partner company REST API
- SaaS API
- Legacy external system

Rules:
- Use `RestClient` or `WebClient`.
- Do not force Feign for external APIs.
- Set timeout, retry, and error handling explicitly.
- Always propagate `X-Trace-Id` when the target allows headers.
- Always write trace events to `trace-service`.

### Client policy
- Internal synchronous calls: `Feign Client`
- External API calls: `RestClient` or `WebClient`
- Trace storage: `trace-service`
- Response header: `X-Trace-Id`

### Common contract in `msa-core`
- `CallBoundaryType`
  - `INTERNAL`
  - `EXTERNAL`
  - `PLATFORM`
  - `PARTNER`
- `CallClientType`
  - `FEIGN`
  - `REST_CLIENT`
  - `WEB_CLIENT`
  - `GATEWAY_PROXY`
  - `MESSAGE_BUS`
- `CallMode`
  - `SYNC`
  - `ASYNC`
- `CallErrorPolicy`
  - `FAIL_FAST`
  - `RETRY`
  - `FALLBACK`
  - `IGNORE`
- `CallConvention`
  - `X-Trace-Id` header name
  - default timeout
  - default retry count
  - internal call policy text
  - external call policy text
  - trace policy text
  - internal package pattern
  - external package pattern

### member-service package layout
- `com.msa.member.client.internal`
  - internal service clients using Feign
- `com.msa.member.client.external`
  - external API clients using RestClient or WebClient
- `com.msa.member.service`
  - business orchestration and trace recording

### auth-service expansion model
- `com.msa.auth.auth.strategy`
  - auth type handlers by strategy
- `com.msa.auth.auth.dto`
  - login request and response objects
- `com.msa.auth.auth.token`
  - JWT token creation
- `com.msa.auth.controller.AuthLoginController`
  - login entry point
- Supported auth types should grow in this order:
  - `PASSWORD`
  - `OTP`
  - `SOCIAL`
  - `MFA`
  - `SSO`
- Sample flow:
  - `POST /auth/signup` creates a user and returns JWT tokens
  - `POST /auth/login` authenticates by `authType` and returns JWT tokens
  - signup data is stored in H2 memory DB
  - login reads the same H2 memory DB
  - restarting the service clears the H2 memory DB, so users must sign up again

### test-service package layout
- `com.msa.test.controller`
  - UI page controller and Gateway proxy API
- `src/main/resources/templates`
  - Thymeleaf pages
- `src/main/resources/static`
  - JavaScript and CSS assets
- No `service`, `mapper`, `xml`, or DB config is required.

### test-service purpose mapping
- `Member Service`
  - member signup and member flow verification through Gateway
- `Auth Service`
  - auth flow verification through Gateway
- `Config Service`
  - config lookup verification through Gateway
- `Trace Service`
  - trace list and trace lookup verification through Gateway
- `Gateway Service`
  - gateway health and routing verification

### member-service examples
- `AuthUserFeignClient`
  - internal service call example
- `EligibilityRestClient`
  - external API call example
- `HrEmployeeFeignClient`
  - internal HR verification example

### auth-service examples
- `AuthStrategy`
  - common authentication contract
- `PasswordAuthStrategy`
  - ID/PW login example
- `JwtTokenService`
  - token creation example

### Standard wording
- Internal call means a platform-owned service-to-service call.
- External call means a call to a system outside the platform boundary.
- Internal synchronous calls use Feign.
- External API calls use RestClient or WebClient.
- All calls must preserve `X-Trace-Id`.
- All traces must be stored in `trace-service`.

### member-service policy
- Internal member flow checks such as HR, dormant-user, and eligibility checks should be treated as internal calls if they are part of the platform.
- True outside-company integration should be treated as external calls.
- Member signup responses must include `processId`, `processType`, and `traceId`.

## Gateway rules
- Gateway is the single entry point.
- Gateway routes to service URLs configured in `gateway-service/src/main/resources/application.yml`.
- Gateway always adds `X-Trace-Id` to the response header.
- Gateway error responses also include `X-Trace-Id`.
- Gateway filters protected routes by `Authorization: Bearer <JWT>`.

## Service run order
Run `trace-service` first, then `gateway-service`, then the business services.

1. `trace-service`
2. `gateway-service`
3. `config-service`
4. `auth-service`
5. `hr-service`
6. `member-service`
7. `test-service`

## Default ports
- `gateway-service`: `8080`
- `trace-service`: `8090`
- `config-service`: `8081`
- `auth-service`: `8082`
- `member-service`: `8083`
- `hr-service`: `8084`
- `test-service`: `8099`

## How to run
Open one terminal per service folder and run:

```bash
gradlew bootRun
```

If you use plain Gradle instead of the wrapper:

```bash
gradle bootRun
```

### Example startup order
Start `trace-service`:
```bash
cd trace-service
gradlew bootRun
```

Start `gateway-service`:
```bash
cd gateway-service
gradlew bootRun
```

Start `config-service`:
```bash
cd config-service
gradlew bootRun
```

Start `auth-service`:
```bash
cd auth-service
gradlew bootRun
```

Start `member-service`:
```bash
cd member-service
gradlew bootRun
```

Start `hr-service`:
```bash
cd hr-service
gradlew bootRun
```

Start `test-service`:
```bash
cd test-service
gradlew bootRun
```

## Member signup
Sample signup flow:
- `member-service` calls `hr-service` first
- if the employee is active, signup continues
- the trace shows HR check success or failure
- `hr-service` is independent and can be deployed or replaced separately

### Request
`POST /members/signup`

### Example
```json
{
  "memberNo": "EMPLOYEE-10001",
  "memberName": "홍길동",
  "status": "ACTIVE"
}
```

### Response
The response includes:
- `processId`
- `processType`
- `traceId`
- `memberNo`
- `memberName`
- `status`

## Trace APIs
### List all traces
`GET /trace-events`

### Search by trace id
`GET /trace-events/trace-id/{traceId}`

### Search by process id
`GET /trace-events/process-id/{processId}`

### Search by process id and trace id
`GET /trace-events/process-id/{processId}/trace-id/{traceId}`

### Search by status
`GET /trace-events/status/{status}`

Example status values:
- `RUNNING`
- `SUCCESS`
- `FAILED`

## Member process APIs
- `GET /members/processes`
- `GET /members/processes/{processId}`
- `GET /members/processes/{processId}/steps`
- `GET /members/processes/{processId}/timeline`

## DB selection
Each service can choose DB type using `app.database-type`.

Supported values:
- `H2`
- `MARIADB`
- `POSTGRESQL`

## Notes
- This platform uses a central `trace-service` instead of local trace tables.
- Gateway and every service must keep trace propagation consistent.
- Internal call and external call boundaries must be decided first before choosing the client.
