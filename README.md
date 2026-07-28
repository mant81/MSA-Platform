# MSA PLATFORM

MSA PLATFORM은 Spring Boot 3, Java 21, Gradle 기반의 트레이스 중심 MSA 샘플 플랫폼입니다.

## 구성 서비스
- `gateway-service`
- `trace-service`
- `member-service`
- `hr-service`
- `auth-service`
- `config-service`
- `test-service`
- `msa-core`

## 서비스 목적
- `gateway-service`
  - 전체 백엔드의 진입점입니다.
  - 요청 라우팅, `X-Trace-Id` 전파, JWT 필터링을 담당합니다.
- `trace-service`
  - 모든 trace를 중앙 저장합니다.
  - trace 조회 API를 제공합니다.
- `member-service`
  - 회원가입과 회원 프로세스 흐름을 담당합니다.
  - 회원가입 시 `hr-service`를 먼저 호출해 정상 직원인지 확인합니다.
- `hr-service`
  - HR 시스템 예시입니다.
  - 직원 여부 확인 전용의 독립 서비스입니다.
  - 다른 시스템과 포맷이 달라도 되도록 별도 계약으로 분리합니다.
- `auth-service`
  - 회원가입과 로그인, JWT 발급을 담당합니다.
  - H2 메모리 DB에 실제 저장하고 조회합니다.
  - 서비스 재시작 시 데이터가 초기화되므로 다시 가입해야 합니다.
- `config-service`
  - 설정 조회와 설정 저장을 담당합니다.
- `test-service`
  - MSA 백엔드 테스트용 Thymeleaf UI입니다.
  - 모든 백엔드 호출은 `gateway-service`를 통해서만 수행합니다.
- `msa-core`
  - 공통 규약, 응답, 예외, 트레이스 모델을 담는 공유 jar입니다.

## 공통 규칙
- 기본 패키지는 `com.msa`를 사용합니다.
- 조회는 `select*` 이름을 사용합니다.
- 저장은 `insert` 이름을 사용합니다.
- 코드는 단순하게 유지합니다.
- DTO는 요청이 없으면 추가하지 않습니다.
- 기본 DB는 H2입니다.
- DB 종류는 서비스별 설정 `app.database-type`으로 선택합니다.

## 개발 방식
이 프로젝트는 `my_harness` 가이드의 원칙을 따릅니다.

### 기본 원칙
- 요구사항을 먼저 좁게 정의하고, 필요한 것만 만듭니다.
- 불필요한 추상화, 과도한 확장성, 미리 넣는 범용 구조는 만들지 않습니다.
- 구조 변경과 기능 변경은 가능하면 분리합니다.
- 변경한 내용은 요청과 직접 연결되어야 합니다.
- 기존에 잘 동작하는 코드는 함부로 손대지 않습니다.

### 작업 순서
1. 요구사항과 현재 구조를 먼저 확인합니다.
2. 필요한 파일만 최소 범위로 수정합니다.
3. 가능한 경우 단순한 예시부터 먼저 만듭니다.
4. 기능이 있으면 검증 가능한 흐름을 우선 추가합니다.
5. 문서와 코드가 서로 다르면 같이 맞춥니다.

### 검증 원칙
- 코드 변경이 있으면 동작 확인 기준도 같이 둡니다.
- 테스트 가능한 로직은 실패 케이스와 성공 케이스를 구분합니다.
- UI나 문서 작업처럼 테스트가 과한 경우에는 목표 달성 여부를 기준으로 확인합니다.
- 한 번에 너무 많은 변화를 넣지 않습니다.

### 하네스 기준 반영
- 에이전트식 작업 방식은 `목표 → 최소 변경 → 검증 → 정리` 순서로 진행합니다.
- 구조 변경과 동작 변경이 섞이면 먼저 구조를 정리한 뒤 기능을 넣습니다.
- 새 의존성은 꼭 필요할 때만 추가합니다.
- 공통 규칙은 `msa-core`에 두고, 서비스별 동작은 각 서비스에서 처리합니다.

## Trace 규칙
- 모든 요청과 응답에는 `X-Trace-Id`가 있어야 합니다.
- 요청에 `X-Trace-Id`가 없으면 Gateway가 생성합니다.
- Gateway는 같은 `X-Trace-Id`를 downstream으로 전달합니다.
- downstream 서비스도 응답 헤더에 `X-Trace-Id`를 다시 실어줍니다.
- 모든 trace는 `trace-service`에 저장합니다.

## 호출 경계 정의

### 내부 호출
플랫폼 내부 서비스끼리의 호출입니다.

예시:
- `gateway-service -> member-service`
- `gateway-service -> auth-service`
- `member-service -> auth-service`
- `member-service -> config-service`
- `member-service -> hr-service`

규칙:
- 내부 동기 호출은 `Feign Client`를 우선 사용합니다.
- `X-Trace-Id`를 그대로 전달합니다.
- trace는 항상 `trace-service`에 기록합니다.

### 외부 호출
플랫폼 외부 시스템으로의 호출입니다.

예시:
- 파트너사 API
- SaaS API
- 레거시 외부 시스템

규칙:
- `RestClient` 또는 `WebClient`를 사용합니다.
- Feign으로 억지로 묶지 않습니다.
- timeout, retry, error handling을 명시합니다.
- 가능한 경우 `X-Trace-Id`를 전달합니다.
- trace는 항상 `trace-service`에 기록합니다.

## JWT 규칙
- JWT 발급은 `auth-service`가 담당합니다.
- JWT 검증과 차단은 `gateway-service`가 담당합니다.
- 보호된 요청은 `Authorization: Bearer <JWT>` 형식을 사용합니다.
- 샘플 JWT는 실제 서명 검증 대신 형식 기반 필터를 사용합니다.

## auth-service 확장 구조
- `com.msa.auth.auth.strategy`
  - 인증 방식별 처리기
- `com.msa.auth.auth.dto`
  - 로그인/회원가입 요청과 응답
- `com.msa.auth.auth.token`
  - JWT 생성
- `com.msa.auth.controller.AuthLoginController`
  - 로그인 진입점

지원 방향:
- `PASSWORD`
- `OTP`
- `SOCIAL`
- `MFA`
- `SSO`

샘플 흐름:
- `POST /auth/signup`
  - 사용자 생성 후 JWT 토큰 반환
- `POST /auth/login`
  - `authType` 기준으로 인증 후 JWT 토큰 반환
- 가입 데이터는 H2 메모리 DB에 저장합니다.
- 로그인은 같은 H2 메모리 DB를 조회합니다.
- 서비스 재시작 시 H2 데이터가 사라지므로 다시 가입해야 합니다.

## hr-service 계약
- `hr-service`는 완전히 독립된 서비스입니다.
- 응답 포맷은 HR 전용으로 별도 유지합니다.
- 다른 서비스는 HR 응답을 직접 공용 VO로 쓰지 않고, 필요한 값만 해석합니다.
- 회원가입 시 HR 확인용으로 사용합니다.

## test-service 목적
- `test-service`는 MSA 백엔드 통합 테스트용입니다.
- 비즈니스 서비스가 아닙니다.
- Thymeleaf UI에서 Ajax로 호출 흐름을 확인합니다.
- DB 연동은 필요 없습니다.
- `service`, `mapper`, `xml`, DB 설정은 두지 않습니다.
- 테스트 요청은 모두 Gateway를 경유합니다.

### test-service 화면 구성
- `Gateway Service`
  - 라우팅 확인
- `HR Service`
  - 직원 확인
- `Member Service`
  - 회원가입과 회원 흐름 확인
- `Auth Service`
  - 회원가입과 로그인, JWT 확인
- `Config Service`
  - 설정 조회 확인
- `Trace Service`
  - trace 조회 확인

## member-service 회원가입 흐름
샘플 흐름:
- `member-service`가 먼저 `hr-service`를 호출합니다.
- 직원이 정상(`ACTIVE`)이면 다음 단계로 진행합니다.
- trace에는 HR 확인 성공/실패가 그대로 남습니다.
- `hr-service`는 독립 서비스이므로 나중에 교체해도 됩니다.

### Request
`POST /members/signup`

### 예시
```json
{
  "memberNo": "EMPLOYEE-10001",
  "memberName": "홍길동",
  "status": "ACTIVE"
}
```

### Response
응답에는 다음 값이 포함됩니다.
- `processId`
- `processType`
- `traceId`
- `memberNo`
- `memberName`
- `status`

## Trace API
### 전체 조회
`GET /trace-events`

### traceId 조회
`GET /trace-events/trace-id/{traceId}`

### processId 조회
`GET /trace-events/process-id/{processId}`

### processId + traceId 복합 조회
`GET /trace-events/process-id/{processId}/trace-id/{traceId}`

### 상태별 조회
`GET /trace-events/status/{status}`

상태 예시:
- `RUNNING`
- `SUCCESS`
- `FAILED`

## member process API
- `GET /members/processes`
- `GET /members/processes/{processId}`
- `GET /members/processes/{processId}/steps`
- `GET /members/processes/{processId}/timeline`

## 서비스 실행 순서
먼저 `trace-service`, 그다음 `gateway-service`, 이후 업무 서비스들을 실행합니다.

1. `trace-service`
2. `gateway-service`
3. `config-service`
4. `auth-service`
5. `hr-service`
6. `member-service`
7. `test-service`

## 기본 포트
- `gateway-service`: `8080`
- `trace-service`: `8090`
- `config-service`: `8081`
- `auth-service`: `8082`
- `member-service`: `8083`
- `hr-service`: `8084`
- `test-service`: `8099`

## 실행 방법
각 서비스 폴더에서 아래처럼 실행합니다.

```bash
gradlew bootRun
```

Gradle wrapper가 없으면:

```bash
gradle bootRun
```

### 실행 예시
`trace-service`
```bash
cd trace-service
gradlew bootRun
```

`gateway-service`
```bash
cd gateway-service
gradlew bootRun
```

`config-service`
```bash
cd config-service
gradlew bootRun
```

`auth-service`
```bash
cd auth-service
gradlew bootRun
```

`hr-service`
```bash
cd hr-service
gradlew bootRun
```

`member-service`
```bash
cd member-service
gradlew bootRun
```

`test-service`
```bash
cd test-service
gradlew bootRun
```

## 주의 사항
- 이 플랫폼은 중앙 `trace-service`를 사용합니다.
- Gateway와 모든 서비스는 trace 전파 규칙을 지켜야 합니다.
- 내부 호출과 외부 호출 경계를 먼저 정한 뒤 클라이언트를 선택합니다.
