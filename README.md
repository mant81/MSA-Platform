# MSA PLATFORM

MSA PLATFORM is a small Spring Boot 3 + Java 21 + Gradle based platform for trace-aware microservices.

## What this platform contains
- `gateway-service`
- `trace-service`
- `member-service`
- `auth-service`
- `config-service`
- `msa-core`

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

### member-service examples
- `AuthUserFeignClient`
  - internal service call example
- `EligibilityRestClient`
  - external API call example

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

## Service run order
Run `trace-service` first, then `gateway-service`, then the business services.

1. `trace-service`
2. `gateway-service`
3. `config-service`
4. `auth-service`
5. `member-service`

## Default ports
- `gateway-service`: `8080`
- `trace-service`: `8090`
- `config-service`: `8081`
- `auth-service`: `8082`
- `member-service`: `8083`

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

## Member signup
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
