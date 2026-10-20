# 단일 VM 개발·운영 전환 runbook

기준일: 2026-10-20 (Asia/Seoul). Redis·Kafka 없이 실행하는 회원가입 샘플이다. 실제 운영 목표·보존·인수 담당자(G0)는 미확정이며 운영 도입 승인을 뜻하지 않는다.

## 실행 모드

- 기본: 서비스별 H2 메모리 DB. Flyway가 처음 한 번 초기 스키마/샘플 데이터를 적용한다.
- `persistent` profile: `DB_URL` 미설정 시 `./data/<service>` H2 파일 DB. 실행 디렉터리와 디스크를 고정한다. `data/`는 버전 관리하지 않는다.
- PostgreSQL: `SPRING_PROFILES_ACTIVE=persistent`, `DB_URL=jdbc:postgresql://host:5432/<service>db`, `DB_USER`, `DB_PASSWORD`를 서비스별로 주입한다. JDBC URL에서 드라이버를 선택한다. 과거 `app.database-type`/`app.datasource-driver` 선택에 의존하지 않는다.
- 기존 `app.datasource-*` 직접 설정도 유지한다. 접속 URL·사용자·비밀번호를 하드코딩해 바꾸지 않는다. 운영에서는 서비스별 DB와 최소 권한 계정을 사용한다.

현재 클라우드 개발 환경:

```bash
source /workspace/cloud-setup/env.sh
./gradlew -I /workspace/cloud-setup/proxy.gradle --project-cache-dir build/verification/gradle-cache --no-daemon --max-workers=2 build
python3 tools/security/prepare_local.py
python3 tools/security/start_service.py trace-service --port 8090
# 다른 터미널에서 auth 8082, config 8081, hr 8084, member 8083, gateway 8080, test 기본 8099(또는 `--port 8085`) 실행
```

새 로컬 자격증명은 ignored `build/security/local-v3`에 생성한다. HR→Trace 대상별 자격이 추가됐으므로 v2 파일을 덮어쓰거나 v2/v3 파일을 섞지 않는다. v2로 실행 중인 프로세스는 자동 재배포되지 않는다. 서비스와 수집기를 같은 v3 집합으로 재시작해야 한다. 개인키는 Auth만, 서비스 비밀은 각 수신 대상별로 구분한다. 재사용 실행 지침은 환경 설정에 저장하며 저장·게시·실행을 구분한다.

## 데이터 설치와 이행

새 DB에는 `spring.sql.init.mode=never`와 Flyway를 사용한다. 버전 SQL을 수정하지 않고 새 migration을 추가한다. clean은 기본 비활성 정책을 유지하며 운영에서 사용하지 않는다. 이전 `schema.sql`은 과거 V1 구조 확인용이며 새 migration과 함께 직접 실행하지 않는다.

지원하는 과거 구조는 커밋 `6d4b385`의 서비스별 V1과 일치하는 DB다. 더 오래된 평문 비밀번호 구조는 먼저 비밀번호 초기화·권한·스키마 이행을 설계해야 한다. 운영 데이터나 중복 행을 자동 삭제·병합하지 않는다.

1. 쓰기 중단 및 백업을 수행하고 복원 가능한 별도 DB를 확인한다.
2. `tools/db/legacy_preflight.py --service member|auth|trace`를 해당 DB의 `PGSERVICE`/`PGPASSFILE`로 실행한다. 데이터 값은 출력하지 않는다. 중복 회원 번호·프로세스·단계, 비밀번호 초기화 대상 등이 있으면 중단한다.
3. 컬럼/제약/샘플 seed까지 V1과 같은지 검토한다. 이 preflight는 전체 스키마 비교를 대체하지 않는다.
4. 정확한 V1로 확인된 DB에서만 일회성 `--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1`을 허용한다. 자동 baseline을 공통 설정에 남기지 않는다. H2의 명시 baseline 및 이행/재실행은 자동 테스트로 검증한다.
5. Flyway validate, 데이터 수·제약·대표 가입·조회·권한을 검증한 뒤 쓰기를 재개한다. 운영 PostgreSQL 과거 데이터 이행은 별도 리허설/인수가 필요하다.

## 이미지와 배포

`deploy/Dockerfile`은 확인한 Java 25 JRE digest를 고정하며 애플리케이션 JAR 하나만 복사한다. `.dockerignore`는 개인키·자격증명·소스·일반 build 출력을 제외한다. 배포 태그는 검증된 커밋 SHA를 사용하고 재사용하지 않는다.

```bash
BUILDX_CONFIG="$PWD/build/verification/buildx" docker build -f deploy/Dockerfile --build-arg SERVICE=trace-service --build-arg REVISION=<commit> -t msa/trace-service:<commit> .
MSA_SECRETS_DIR=/secure/msa IMAGE_TAG=<commit> docker compose -f deploy/compose.yml config --quiet
# 7개 검증 이미지와 서비스별 Secret/DB 설정을 준비한 뒤 같은 환경 변수로 compose up -d
```

Compose는 서비스별 PostgreSQL DB, 내부 네트워크, 읽기 전용 앱 파일 시스템·임시 /tmp, UID/GID 10001, capability 제거와 종료 유예를 정의한다. Gateway/UI만 호스트 loopback 포트에 공개한다. 최초 DB 초기화 스크립트는 빈 볼륨에서만 실행한다. VM 외부 공개는 검증된 TLS reverse proxy와 망 정책을 먼저 적용한다. Compose 전체 배포/TLS/인증서 갱신은 본 로컬 검증의 완료 항목이 아니다.

Secret 디렉터리 구조:

```text
/secure/msa/
  <service>-service.properties   # 각 서비스 자신의 outbound/trusted 정보
  public/current.pem, next.pem
  current.key                   # Auth에만 mount
  postgres_admin_password       # PostgreSQL에만 mount
  db/<service>/DB_PASSWORD      # 해당 서비스 및 최초 DB 초기화에만 mount
```

앱이 읽는 파일은 전용 UID 10001 또는 전용 읽기 그룹에만 권한을 부여한다. 개발용 mode 0600 파일은 UID가 달라 그대로 읽히지 않을 수 있다. 권한을 전체 공개로 풀지 않는다. 컨테이너 검증은 호스트 비특권 UID를 별도 지정해 자체 시험 파일을 읽었다. PostgreSQL 운영 계정은 DB 관리 계정과 분리한다.

Auth 최초 관리자 생성이 필요하면 Auth 전용 configtree 디렉터리에 `MSA_BOOTSTRAP_ADMIN_USER`, `MSA_BOOTSTRAP_ADMIN_PASSWORD` 파일을 일회성 주입하고 생성 후 제거한다. 기본 관리자·기본 비밀번호는 제공하지 않는다. `MSA_ENVIRONMENT`, `MSA_INSTANCE_ID`, `MSA_DEPLOYMENT_VERSION`을 실제 배포 값으로 설정한다.

## 업무·수집 장애

- 가입은 `Idempotency-Key`를 사용한다. 미입력 시 회원 번호를 사용하며 사용자별로 구분한다. 같은 키·같은 입력은 저장된 원래 응답을 반환한다. 키에 다른 입력 또는 진행 중 요청은 409다.
- 업무 상태·단계·회원 저장·Outbox·응답이 같은 로컬 트랜잭션으로 commit된다. DB 오류는 전부 rollback한다. 외부 쓰기/보상은 이 샘플에 포함되지 않는다.
- 최초 claim은 별도 commit된다. 5분 이상 미종료 claim은 `RECOVERY_REQUIRED`로 바뀌며 자동 재실행하지 않는다. DB 상태·진행 중 트랜잭션·외부 부작용 가능성을 확인한다. 범용 중간 단계 재개·외부 보상 API는 후속이다. 아래 승인 기반 로컬 재시도 절차를 사용한다.
- 중요한 Member 가입 이벤트는 DB Outbox에서 최대 100건씩 전송한다. 수집기가 반환한 eventId별 PERSISTED/DUPLICATE만 승인한다. 타임아웃/승인 누락/부분 처리 시 미승인 이벤트를 지수 지연(최대 60초) 후 재전송한다. 같은 `(sourceService,eventId)`는 중앙에서 한 번 저장한다.
- Gateway/HR 요청 진단 이벤트는 유한 메모리 버퍼의 best effort다. `msa_trace_dropped`, `msa_trace_failed`와 `msa_outbox_pending`, `msa_outbox_retrying`을 확인한다. 디스크 부족 때 업무와 Outbox 원자성을 깨고 성공 처리하지 않는다.
- 종료 응답이 없는 claim을 근거 없이 지우거나 키를 바꿔 재시도하지 않는다. 가입 샘플에는 회원 번호 unique 제약이 있으나 실제 외부 부작용까지 중복 방지하는 것은 아니다.
- HR 예산: 기본 connect 500ms/read 1000ms, 자동 Retry 금지. Circuit은 최근 10건·최소 5건·실패율 60%에서 열리고 5초 후 한 번 시험해 복구한다. 운영 예산은 실제 업무의 전체 응답 목표에 맞춰 확정한다.

## 상태·장애·알림

`APP_MONITOR_TARGETS=hr-1=hr-service@http://127.0.0.1:8084,...`로 신뢰된 기대 인스턴스를 명시한다. API 사용자가 probe URL을 등록할 수 없고 리다이렉트를 따르지 않는다. Readiness와 liveness 그룹은 모든 백엔드에 노출한다. Readiness는 자체 수용 상태와 DB를, liveness는 JVM 생존을 판단한다.

기본 정책 1: 5초 주기, 1초 probe 기한, 최초 실패 DEGRADED, 연속 3회 실패 DOWN, 연속 2회 정상 UP, 마지막 관측 30초 초과 UNKNOWN. 수집 중단·DB 불능은 UNKNOWN/수집기 자체 감시로 별도 판단해야 한다. 서비스 집계는 미관측 포함 시 UNKNOWN, 모두 UP일 때 UP, 모두 DOWN일 때 DOWN, 나머지는 DEGRADED다. UNKNOWN을 UP으로 바꾸지 않는다.

DOWN 전환에 incident 하나를 열고 UP 복구에 닫는다. OPERATIONS/ADMIN은 ACK할 수 있으며 같은 incident 재확인은 중복 기록하지 않는다. ADMIN 정비 설정은 1시간 동안 새 incident를 억제하고 실제 상태는 유지한다. 해제 후 지속 DOWN이면 incident를 연다. 기대 목록에 없는 인스턴스 탐지·자동 등록/제거·다중 수집기 조정·Gateway 자동 트래픽 제외는 후속 운영 작업이다.

`APP_ALERT_WEBHOOK_URL`은 운영 설정으로 주입한다. 기본 미설정이며 화면에 대기 건수와 미설정을 표시한다. HTTPS만 허용하며 토큰은 `APP_ALERT_TOKEN`으로 외부 주입한다. 시험에서만 명시적 loopback HTTP를 허용한다. 장애/복구 eventId별 Outbox와 최대 300초 재전송을 사용한다. 수신자는 Idempotency-Key로 중복을 제거해야 한다. 실제 조직 채널에는 전송하지 않았고 수신 담당자·전파/당직 체계는 미확정이다.

## 지표와 보존

`/actuator/prometheus`는 운영 JWT로 보호하며 백엔드 직접 접근에는 대상별 서비스 인증도 필요하다. 무인증 공개를 하지 않는다. `deploy/prometheus.yml`, `rules.yml`, `grafana-dashboard.json`은 수집/화면 구성 초안이다. 실제 Prometheus/Grafana 서버 설치·자격 갱신·전체 서비스 scrape·경보 평가를 완료한 것으로 보지 않는다. traceId/processId/memberNo를 메트릭 라벨로 사용하지 않는다.

오류 수집은 서비스·코드·배포 버전으로 그룹화하며 허용된 식별자/상태/시간만 저장한다. 수집기에 전달된 오류 자유 텍스트는 안전한 코드 설명으로 치환한다. 요청/응답 본문·토큰·비밀번호·stack을 수집하지 않는다. coarse code 그룹은 서로 다른 callsite를 합칠 수 있다. 업무 키는 개인 식별 정보가 될 수 있으므로 운영 조회 역할과 보존 정책을 적용한다.

Trace TTL은 `APP_RETENTION_ENABLED=true`, `APP_RETENTION_TRACE_DAYS=<확정값>`으로 명시적 활성화한다. 기본 비활성이고 제안값은 30일이며 한 번에 500건 삭제한다. projection은 남을 수 있어 흐름 조회는 항상 완전성을 UNVERIFIED로 표시한다. Member history/Outbox/Refresh/감사·incident 전체 보존과 디스크 상한 정책은 G0 확정 후 보완해야 한다. 미전송 중요 이벤트를 TTL로 지우지 않는다.

## 백업·복원·롤백

암호화·접근 제한된 저장소에 서비스별 `pg_dump -Fc`와 migration·이미지·설정 버전을 함께 보관한다. restore는 먼저 새 DB에 `pg_restore --exit-on-error`로 수행하고 데이터 수·조회·권한·업무·Outbox를 검증한다. 자동 PG 시험의 DB 계정은 격리 fixture의 postgres이며 운영 최소 권한 계정 인수를 대체하지 않는다. 이번 자동 시험은 Member DB를 별도 DB에 복원해 회원/프로세스/Outbox 수를 비교하고 DB 재시작도 확인한다.

앱 롤백은 현재 이벤트/인증/DB 계약과 호환 검증한 이전 이미지로만 수행한다. 새 Outbox 이벤트를 모르는 과거 수집기로 임의 회귀하면 승인 후 metadata가 유실될 수 있다. 자동 down migration·데이터 삭제·기존 백업의 덮어쓰기는 제공하지 않는다. 운영 롤백 실연·RTO/RPO·PITR·HA·장기 보존 인수는 별도 필요하다.

## 중단된 Member 가입의 승인 기반 재시도 (WBS 4.2)

1. OPERATIONS/ADMIN으로 `GET /members/requests`에서 복구 대기를 확인한다. 100건씩 requestKey cursor로 읽는다. 최초 Trace, 마지막 상태, 기존 회원/성공 프로세스와 DB 복원 여부를 확인한다.
2. 실제 진행 중 작업과 외부 쓰기 부작용이 없는지 확인한다. 자동 watchdog이 5분 경과 요청을 RECOVERY_REQUIRED로 바꾸더라도 그 표시만으로 외부 부작용 없음이 증명되지는 않는다. 이 샘플의 HR 호출은 읽기이고 Member 저장/상태/Outbox는 같은 DB 트랜잭션이다.
3. ADMIN으로 POST `/members/requests/{requestKey}/allow-retry`를 호출한다. reasonCode는 DB_ROLLBACK_CONFIRMED 또는 PROCESS_CRASH_CONFIRMED다. 승인과 감사는 원자적이며 업무를 실행하지 않는다.
4. 원래 사용자가 원래 Idempotency-Key·동일한 회원 정보로 POST /members/signup을 재호출한다. 다른 입력·진행 중 중복은 409다. 한 번의 재시도가 소비되고 성공/업무 거부 응답은 기존 정책대로 캐시된다. 다시 DB 오류가 나면 RECOVERY_REQUIRED로 복귀한다. 최대 실제 재시도는 3회다.
5. request 감사에서 승인자/원래 사용자/사유/Trace/시각을 확인하고, 성공 프로세스·회원 한 건·Outbox 복구를 확인한다. 첫 시도의 Trace는 initialTraceId, 재시도 Trace는 감사 RETRY_STARTED에서 연결한다.

완료 응답이 있거나 회원/성공 프로세스가 이미 있으면 승인하지 않는다. V5 이전 행의 owner/member가 없거나 한도를 소모했으면 수동 원인 분석 대상으로 남긴다. 임의 claim 삭제·입력 변경·다른 키로의 강제 재실행을 복구 절차로 사용하지 않는다. 실제 외부 부작용·이행 DB/운영 권한·인수는 별도 검증이 필요하다.

### 단계 이력 확인

업무 ID로 `/operations/flows`를 조회하고 `steps`의 실행 ID·시도·종료 상태를 확인한다. 목록의 `stepsTruncated`가 true이면 원본 이벤트 cursor 조회로 추가 실행을 확인한다. 기간으로 잘린 이벤트 목록과 전체 수집 projection은 범위가 다르다. 원본 이력이 보존 정책으로 삭제된 경우 projection만으로 원인이나 전체 수집을 확정하지 않는다. UNVERIFIED는 업무 실패 판정이 아니며, Member 원본 업무/Outbox 배달 상태와 대조한다. 예상 이벤트 manifest·운영 보존 이행 정책의 인수가 남아 있다.
