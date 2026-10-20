# 회원가입 샘플 보안 기준 — WBS 3단계

Java 25 / Spring Boot 4.1.1의 BOM이 관리하는 Spring Security 7.1.1을 사용한다. 사용자 확인 범위인 단일 VM·회원가입 샘플의 개발 기준이며, 운영 환경/SLO/인수 확정이나 침투 시험 완료를 의미하지 않는다.

## 1. 인증 경계

| 경로/대상 | 조건 |
|---|---|
| Gateway POST /auth/signup, /auth/login, /auth/refresh, /auth/logout | 정확한 경로만 공개, 입력/토큰 검증은 Auth |
| Gateway 및 서비스 GET /actuator/health 와 하위 health 경로 | 최소 상태만 공개, 다른 관리 endpoint는 노출하지 않음 |
| GET /members 및 /hr/employees | USER 또는 ADMIN |
| POST /members/signup | USER 또는 ADMIN |
| GET /members/processes 및 하위 조회 | OPERATIONS 또는 ADMIN |
| POST /members | ADMIN |
| GET /members/requests 및 감사 조회 | OPERATIONS 또는 ADMIN |
| POST /members/requests/*/allow-retry | ADMIN |
| GET /configs | USER, OPERATIONS 또는 ADMIN |
| POST /configs | ADMIN |
| GET /users | ADMIN; 비밀번호/해시 제외 |
| GET /trace-events, /operations 및 하위 조회 | OPERATIONS 또는 ADMIN |
| POST /operations/incidents/*/ack | OPERATIONS 또는 ADMIN |
| POST /operations/services/*/maintenance | ADMIN |
| GET /actuator/prometheus | OPERATIONS 또는 ADMIN; backend는 서비스 인증 추가 |
| 외부 POST /trace-events, 미등록 경로·관리 쓰기 | 거부 |
| 개별 업무 서비스 | 유효한 호출 서비스 자격증명 + 해당 사용자 JWT/권한 모두 필요 |
| 내부 POST /trace-events 및 /trace-events/batch | 허용된 수집 발행자 자격증명 필요, 사용자 JWT는 요구하지 않음 |

공개 가입은 USER만 생성한다. role/status/passwordHash 입력으로 권한을 부여할 수 없으며 기존 POST /users 쓰기 API는 제거했다. OPERATIONS 발급은 운영에서 통제한 역할 프로비저닝이 필요하다. 별도 역할 관리 API나 SSO/OTP는 추가하지 않았다.

Gateway는 클라이언트가 보낸 X-Service-Id/Token을 전달하지 않고 자기 자격증명으로 덮어쓴다. 개별 서비스도 사용자 JWT를 독립 검증하므로 서비스 자격증명만으로 사용자를 가장할 수 없다. 반대로 사용자 JWT만으로 내부 서비스를 직접 호출할 수도 없다.

## 2. 비밀번호와 기존 데이터

- BCrypt cost 12. 가입은 12~64자, UTF-8 최대 72바이트. ID는 영문·숫자·점·밑줄·하이픈 3~50자.
- 일반 조회 SQL에서 password_hash를 제외하고 VO 직렬화에서도 JsonIgnore로 차단한다. 비밀번호 원문·해시는 응답/Trace에 기록하지 않는다.
- 없는 사용자와 틀린 비밀번호는 같은 401/LOGIN_INVALID. 없는 사용자에도 dummy BCrypt 비교를 수행한다.
- 기존 평문 데이터에 로그인 fallback을 제공하지 않는다. 개발 H2는 재가입, 기존 영속 DB는 별도 migration과 안전한 비밀번호 초기화가 필요하다. 현재 schema.sql의 create-if-not-exists는 기존 테이블을 자동 변경하지 않는다.
- 사용자 ID 유일 제약으로 중복 가입을 409로 거부한다. 가입 사용자·세션·감사는 같은 로컬 트랜잭션에서 생성한다.

## 3. JWT와 키

RS256만 허용한다. Spring/Nimbus 검증으로 서명, 신뢰 kid, issuer, audience, exp/iat/nbf/sub, token_use=access, 역할 목록을 확인한다. alg=none·접두사 토큰·변조·다른 키/대상·누락된 필수 claim은 거부한다. aud는 JWT 표준에 따라 문자열 또는 배열일 수 있으며 검증 시 대상 포함 여부로 정규화한다.

기본 Access TTL은 900초, 설정 허용 범위는 60~3600초다. 시계 오차 허용은 30초이며 서버 시간 동기화가 필요하다.

| 설정 | 적용 |
|---|---|
| MSA_JWT_PUBLIC_KEY_DIRECTORY | 모든 backend의 신뢰 공개키 디렉터리. 파일명 <kid>.pem, X.509 PUBLIC KEY |
| MSA_JWT_PRIVATE_KEY_FILE | Auth만 사용하는 PKCS#8 RSA PRIVATE KEY |
| MSA_JWT_KEY_ID | Auth 서명 kid, 기본 current. 같은 이름의 공개키와 일치 필요 |
| MSA_JWT_ISSUER / MSA_JWT_AUDIENCE | 기본 개발값 https://msa.local/auth / msa-platform, 운영은 환경별 확정 |
| MSA_ACCESS_TTL_SECONDS | Auth 발급 TTL, 기본 900 |
| MSA_SERVICE_CREDENTIALS_FILE | 프로세스별 서비스 identity/trusted 파일, 필수 |
| MSA_BOOTSTRAP_ADMIN_USER / MSA_BOOTSTRAP_ADMIN_PASSWORD | Auth의 선택적 초기 관리자 생성. 둘 다 필요하며 공개 가입과 별개 |

키와 서비스 자격증명이 없거나 잘못되면 시작이 실패한다. 소스의 공용 기본 키/토큰으로 대체하지 않는다. Bootstrap은 ADMIN이 없을 때만 새 관리자 ID를 만들고, 기존 USER를 자동 승격하지 않는다. 운영에서는 단일 통제 작업으로 실행하고 bootstrap 설정을 제거한다.

### 키 교체

1. 검증기마다 기존+신규 공개키를 배포하고 재시작한다. 공개키 목록은 시작 시 읽는다.
2. Auth의 개인키/kid를 신규 쌍으로 교체하고 재시작한다.
3. 이전 발급 토큰의 최대 TTL+시계 오차와 배포 시간을 지난 뒤 이전 공개키를 제거하고 검증기를 재시작한다.
4. 침해 대응 시에는 이전 키 즉시 제거로 기존 토큰을 무효화하고 재로그인을 요구한다. 정상 교체와 달리 세션 중단이 발생한다.

공개키 중첩 신뢰는 자동 테스트에서 확인한다. 실제 운영의 롤링 배포·Secret 배포·키 폐기 실연은 WBS 8/9번 인수 대상이다.

## 4. 내부 서비스 자격증명

호출자·수신자 조합마다 다른 비밀값을 만들고, 프로세스마다 다음 형식의 별도 Properties 파일을 사용한다. **아래는 형식이며 실제 값은 소스에 넣지 않는다.**

```properties
identity.id=<서비스명>
outbound.<대상 서비스명>=<그 대상 전용의 최소 32자 고엔트로피 비밀값>
trusted.<허용된 호출 서비스명>=<그 호출자의 비밀값>
```

호출은 대상별 outbound 값으로 X-Service-Id/X-Service-Token 헤더를 구성하고 수신자는 자기 trusted 값과 상수 시간 비교한다. 다른 대상의 Gateway 비밀값을 재사용할 수 없으며 중복된 인증 헤더는 거부한다. id는 spring.application.name과 같아야 한다. 토큰/신뢰 맵은 로그 문자열에서 마스킹한다.

| 수신 서비스 | 신뢰 호출자와 용도 |
|---|---|
| Auth, Member, Config | Gateway |
| HR | Gateway 또는 Member, 사용자 JWT도 검증 |
| Trace | Gateway 조회/발행, Auth/Member/Config/HR는 POST /trace-events 또는 /trace-events/batch 발행만 |

Trace 저장의 sourceService는 인증 필터가 확정한 서버 내부 request attribute에서 부여한다. payload의 동일 필드는 덮어쓴다. 기존 serviceName은 업무 단계의 대상 등 메타데이터이며 인증 주체와 구분한다.

비밀값은 파일 권한·별도 OS 계정/Secret mount로 격리하고 환경별로 다르게 생성한다. 자격증명 교체 시 수신자 신뢰 설정과 발행자를 함께 갱신·재시작해야 한다. 현재 단일 토큰 방식에는 서비스별 구/신 토큰 무중단 교체가 구현되지 않았다.

모든 서비스의 기본 bind는 127.0.0.1이다. 개발 HTTP는 단일 VM loopback에 한정한다. 외부 노출은 TLS reverse proxy와 망 정책을 별도로 구성한다. 서비스가 다른 호스트/컨테이너로 이동하면 TLS/mTLS·사설 네트워크·계정별 Secret 권한을 배포 단계에서 반드시 적용·검증한다. 이 작업에서 방화벽이나 TLS 인증서를 임의 설정하지 않았다.

## 5. Refresh Token

- 256비트 난수의 opaque bearer, Base64URL 43자. DB에는 SHA-256 해시만 보관한다.
- 세션 family 최대 수명은 생성 시점부터 7일. 회전으로 무한 연장하지 않는다.
- POST /auth/refresh의 JSON은 refreshToken 하나를 사용한다. 기존 사용 토큰을 소비하고 새 토큰을 반환한다.
- family SELECT FOR UPDATE와 재조회로 동시 회전을 직렬화한다. 재사용이 확인되면 family와 모든 후속 Refresh Token을 폐기한다.
- 재사용 폐기 시 예외를 던져 트랜잭션이 rollback되지 않도록 실패 결과를 반환해 폐기/감사를 commit한다.
- POST /auth/logout은 해당 family를 폐기하고 204를 반환한다. 알 수 없는 토큰도 204로 처리한다.
- session 생성·회전·재사용·만료/폐기·logout은 auth_security_event에 기록한다. 원문 토큰/비밀번호는 감사에 기록하지 않는다.
- 클라이언트는 refresh 요청을 직렬화해야 한다. 같은 토큰을 동시에 보내면 한 건만 성공하지만 재사용 탐지가 그 성공 토큰의 family도 폐기한다.

**Access JWT는 stateless이므로 logout/refresh 폐기 후에도 기존 exp+시계 오차까지 유효할 수 있다.** 즉시 Access 폐기가 필요한 운영 요건은 introspection/denylist 등 별도 설계가 필요하다. Redis는 도입하지 않았다.

## 6. 개발 실행과 UI

```bash
python tools/security/prepare_local.py
python tools/security/start_service.py trace-service
# 별도 터미널에서 gateway/auth/config/hr/member/test 서비스도 시작
python tools/verification/stage1_baseline.py --stage3 --java "$JAVA_HOME/bin/java"
```

start_service.py는 foreground에서 Java로 exec하며 프로세스별 자격증명과 공개키를 주입한다. Auth에만 개인키/개발 관리자 bootstrap을 전달한다. 기존 터미널에서 직접 gradlew bootRun을 실행하려면 위 환경 설정을 별도로 주입해야 한다.

실행 도구는 Python 3.10 이상·OpenSSL 실행 파일·Java 25가 필요하다. prepare_local.py는 ignored build/security/local-v3 아래의 권한 700 디렉터리와 600 개인키/자격증명 파일을 사용한다. 기존 완성 fixture는 재사용한다. 개발 관리자 정보는 같은 디렉터리의 fixture.json에만 보관하며 채팅·로그에 출력하지 않는다. 파일을 지우면 키/자격증명이 바뀌므로 모든 개발 서비스를 같은 fixture로 재시작해야 한다. 이것은 운영 Secret 발급 기능이 아니다.

UI는 Access/Refresh를 페이지 메모리에만 두며 새로고침 시 재로그인한다. 토큰 응답은 no-store이고 공개 인증/갱신/로그아웃에는 오래된 Authorization을 전달하지 않는다. UI의 토큰 표시 기능은 테스트 용도다. 운영 BFF/HttpOnly cookie 설계·XSS/CSP 점검·TLS·Rate Limit과 보안 인수는 별도로 필요하다.

## 7. 인수 경계

현재 범위는 H2 기반 샘플 검증이다. 운영 DB migration·감사/Refresh 데이터 보존·만료 행 정리·백업/복구·HA·Secret 배포·전문 보안 점검은 WBS 4/8/9번에 남는다. 기존 비H2 DB 설정의 하드코딩된 주소/샘플 계정도 운영 사용 전에 외부화해야 한다.

운영 가용성/SLO·RTO/RPO·키 관리 책임자·보안/업무 인수자가 미확정이므로 전체 차세대 운영 도입 가능으로 판정하지 않는다.


후속 개발에서는 HR의 진단 Trace 쓰기와 `/trace-events/batch`, OPERATIONS/ADMIN의 `/operations/**` 조회·ACK, ADMIN의 정비 변경을 추가했다. 동일한 수신 대상별 인증과 JWT/RBAC 경계를 유지한다. v3 개발 자격증명을 사용하며 과거 v2 파일을 자동 덮어쓰지 않는다. 상세 실행·배포 조건은 [운영 runbook](../operations/RUNBOOK.md)을 따른다.

후속 권한 검토에서 Member의 `/members/processes` 및 하위 조회도 OPERATIONS/ADMIN으로 제한했다. 일반 사용자의 단순 POST `/members`는 거부하고 추적·중복 방지가 있는 `/members/signup`을 사용한다. Gateway와 backend에서 같은 경계를 적용한다.
