# WBS 후속 개발 결과 — 처리 flow·오류·서비스 상태

기준일: 2026-10-20 (Asia/Seoul). 회원가입 샘플·단일 VM·Redis/Kafka 미도입 범위다. **4단계 핵심과 5~8단계의 기본 기능을 구현하고 로컬 개발 검증을 수행했다. WBS 전체 완료·운영 즉시 도입·9.4 인수를 선언하지 않는다.** 이전 main baseline은 `6d4b385`이며 이번 후속 변경은 현재 main 작업 디렉터리에 있다.

## 구현

| WBS | 이번 산출물 | 남은 완료 기준 |
|---|---|---|
| 4.1 | 서비스별 Flyway, H2 파일/PG profile, JDBC URL 드라이버·계정 반영, unique/index, 신규/반복/보존 시험 | 실제 기존 PG 데이터 이행·DB 권한/Secret 인수 |
| 4.2 | 사용자별 DB idempotency claim·입력 해시·동시 중복 차단, 회원/상태/단계/Outbox/응답 원자성, stepExecutionId/attempt, 고아 claim RECOVERY_REQUIRED | 실제 업무의 재개·보상/재시도 정책·범용 재개 API |
| 4.3 | W3C traceparent, Reactor 요청별 문맥·Feign 전파, 단계별 span·HR server 부모 연결, legacy 조회·배포/환경/인스턴스 | 다중 인스턴스·실제 병렬/외부 연계·전체 표준 계약 인수 |
| 4.4 | 중요 Member 이벤트 DB Outbox, 100건 batch·개별 승인, 중복 제거·지수 재전송, JVM 재시작 복구, 비내구 진단 유실 지표 | 포화/디스크 부족·multi-exporter 운영 정책·공간 상한 시험 |
| 4.5 | terminal projection의 버전/종료 역행 방지, bounded cursor·기간 시작/업무 필터·완전성 UNVERIFIED | 단계 projection·기간 끝·누락 확정·정식 v1 수집 계약 |
| 4.6 | Feign 시간 예산·자동 Retry 금지, 유지보수 라이브러리 Circuit 차단/half-open 복구 | 업무별 SLO·write retry/보상 인수 |
| 5.1~5.2 | 안전한 일반 예외 응답, Member errorId→Gateway causedByErrorId, 서비스/코드/배포 그룹·영향/원인 조회 | 전체 Auth/인증 오류 계약 통일·callsite 정규화·실제 root 원인 인수 |
| 5.3 | 운영 ACK/정비 감사, 수집 오류 자유 텍스트 치환, 선택적 bounded Trace TTL | 전 서비스 변경 감사·업무/보안/Outbox/incident 보존·개인정보 정책 |
| 6.1~6.2 | liveness/readiness·graceful 설정, 기대 인스턴스 probe, 4상태·정책/근거·상태 이력 | Gateway 자동 트래픽 제외·동적 등록/폐기·다중 수집기/인스턴스 실기 |
| 6.3 | 인증 Prometheus HTTP·JVM/DB 지표, histogram·Outbox/유실/incident 지표, 수집 규칙/Grafana JSON | 실제 전체 scrape·JWT 갱신·Grafana 실행·경보 평가 |
| 6.4 | incident·중복 억제·ACK·정비, DB webhook Outbox·장애/복구 로컬 수신·실패 재전송 | 운영 수신처/당직·담당/전파·수신 인수 |
| 7.1~7.3 | 운영 화면, 4상태·시각/근거, 오류 그룹→영향→flow, 단계 소요 비교·시도 표시, 메모리 토큰·역할 거부 | 실제 offset waterfall·원인 span 상세·배포 이력·실제 브라우저 인수 |
| 8.1~8.2 | 고정 JRE digest·비특권/읽기 전용 이미지, Compose/Secret/서비스별 DB·CI 빌드/회귀/이미지 생성 | 운영 TLS/인증서·전체 신규 배포·취약점/이미지 검사·원격 CI 실행 |
| 8.3 | PG dump→별도 DB restore·수 비교, DB 재시작 보존·복구 runbook | 호환 앱 rollback 실연·운영 RTO/RPO/PITR/HA 인수 |
| 9.1~9.3 | 실제 UI/Gateway/backend 회귀·보안/중복/역순·중단/재시작·소규모 시험 부하 | 실제 업무 인수표·장기 부하/용량/보존 비용·포화/공간 부족·전문 보안 시험 |
| 9.4 | 사용자 확인의 샘플/VM 범위 유지 | 실제 차세대 업무·업무/운영 인수자·교육/실연 미확정, 미착수 |

상세 상태는 [WBS](../MSA_ADOPTION_WBS.md)와 [CSV](../MSA_ADOPTION_WBS.csv)를 함께 갱신했다. 최초 95~154 PD는 재추정하지 않았으며 임의 완료율/잔여 공수 감산을 하지 않았다.

## 검증 증적

| 구분 | 결과 |
|---|---|
| Java 25 전체 build·JUnit | 39건, 실패/오류/skip 0 — [요약](evidence/test-summary.json) |
| H2 실제 HTTP 회귀 | 132체크 PASS, FAIL/BLOCKED/ERROR 0 — [증적](evidence/h2-regression-report.json), run `20261020T090046Z-06f584` |
| PostgreSQL 실제 HTTP 회귀 | 133체크 PASS, FAIL/BLOCKED/ERROR 0 — [증적](evidence/postgres-regression-report.json), run `20261020T085958Z-e68a39` |
| DB 설치/복원/재시작 | 4체크 PASS — [증적](evidence/database-drill-report.json) |
| 이미지 실제 실행 | 6체크 PASS — [증적](evidence/image-drill-report.json) |
| v3 foreground 실행 도구 | Readiness UP·직접 무인증 운영 요청 401 — [증적](evidence/launcher-report.json) |
| Node UI | 토큰/Refresh/로그아웃 기존 시험 및 운영 로그인·텍스트 렌더·페이지·권한 오류·정보 정리 시험 PASS |
| 이벤트 schema 초안 | 긍정 5·부정 25 fixture PASS; 실제 API의 완전한 v1 구현 판정과 구분 |
| 설정·문서 | Compose config, shell/Python/JS 문법, 문서 링크, diff whitespace 확인 |

HTTP 체크 수에는 환경별 시작 확인 7건이 포함된다. 같은 업무를 두 DB에서 검증한 수를 고유 기능 수로 합산하지 않는다. 테스트 도구가 생성한 키·토큰·관리자 비밀번호·DB 비밀번호·dump는 ignored build에만 두고 문서 증적에는 포함하지 않았다. 생성된 자격증명과 Git 후보 파일의 값 비교도 수행했다. 이 검사는 전문 secret scanner/취약점 검사를 대체하지 않는다.

중요 회귀: 6개 동시 가입 요청의 단일 회원/프로세스, 입력 충돌 409, 실패 저장 시 전체 rollback, 미승인/일부 승인/알 수 없는 승인 보존, 재전송 중복 방지, terminal보다 늦은 START의 상태 역행 방지, W3C 단계→HR 부모 span, 원인 오류↔Gateway 전파 오류 연결, USER 운영/ACK 거부, 실제 probe UP/DOWN·stale UNKNOWN·정비/ACK, webhook 503 재전송·장애/복구 로컬 수신, TTL 및 Secret 비노출 경계를 확인했다. JVM 중단+Outbox 복구는 영속 PostgreSQL 경로에서 실행했다.

### 시험용 읽기 부하

- H2: 100 read requests / concurrency 8; observed RPS=81.9, p95=126.0ms, p99=136.3ms; local trial only
- PostgreSQL: 100 read requests / concurrency 8; observed RPS=73.8, p95=143.9ms, p99=165.3ms; local trial only

100건·동시 8의 인증된 회원 읽기 시험이며 요청 오류 여부와 관측값만 기록했다. 여러 검증 프로세스가 함께 실행된 로컬 환경의 값이다. 최대 처리량·운영 SLO·p99 안정성·장기 용량을 인증한 수치가 아니다.

### 발견·수정과 제한

- PG 최초 시험에서 Trace/HR의 H2 고정 드라이버 문제를 발견했다. JDBC URL에서 드라이버를 고르게 수정 후 실제 5개 DB 연동을 재시험했다.
- PostgreSQL 이미지 초기화의 임시 Unix-socket 서버를 정상 DB로 오인하는 readiness race를 수정했다. 최종 TCP listener 준비를 확인한 뒤 실제 앱 시험을 시작한다.
- 처음 HTTP fault proxy는 chunked request를 읽지 못했다. 정상 전송을 떨어뜨리는 시험 도구 문제를 수정하고 실제 batch 수집·복구를 재확인했다.
- 컨테이너 최초 시험은 호스트 JAR mode 0600 때문에 다른 비특권 UID에서 읽지 못했다. 이미지 COPY에 명시적 read-only mode를 적용한 최종 이미지가 통과했다.
- Circuit 회복 시험의 Mockito stubbing이 기존 예외를 다시 실행하는 fixture 오류를 수정했다. 실제 half-open→CLOSED 확인은 유지했다.
- 기존 Member 프로세스/단계 조회의 USER 접근과 단순 회원 입력 우회를 차단하고 Gateway/backend 역할 경계를 통일했다. 기존 회귀의 운영 조회도 운영 자격으로 재검증했다.
- 브라우저 다운로드가 `cdn.playwright.dev` 네트워크 정책 403으로 거부됐다. 우회하지 않았다. Node DOM+실제 HTTP 중계 검증은 통과했지만 실제 브라우저/접근성 인수와 구분한다.
- 실제 운영 Secret/TLS/채널/요구·SLO·인수 담당자·차세대 실제 업무가 없어 G0~G5 운영 완료로 판단하지 않는다. 로컬 수신 서버 외 조직 메시지를 발송하지 않았다.

## 재현과 인계

[runbook](../../operations/RUNBOOK.md) · [운영 API/호환 범위](../../operations/API.md). 클라우드의 Java/proxy 초기화 후 다음을 실행한다.

```bash
./gradlew -I /workspace/cloud-setup/proxy.gradle --project-cache-dir build/verification/gradle-cache --no-daemon --max-workers=2 build
node tools/verification/ui_security_test.cjs
node tools/verification/operations_ui_test.cjs
python3 tools/verification/validate_contract.py
python3 tools/verification/stage1_baseline.py --next --java "$JAVA_HOME/bin/java"
python3 tools/verification/postgres_application_drill.py
python3 tools/verification/container_drill.py
python3 tools/verification/check_generated_secrets.py
```

CI의 `tools/verification/verify.sh`는 일반 환경에서 동일 Java/계약/UI/H2 흐름을 실행한다. 별도 job step에서 PG를 검증한 뒤 이미지를 만든다. Workflow 파일 작성·로컬 실행과 원격 GitHub Actions/보안 스캔 실행은 구분한다.

재사용 환경 설정의 `start_skill`을 v3 자격증명·운영 화면·격리 시험 기준으로 저장했다. install_script/network/secret 값은 이번에 변경하지 않았다. draft 저장은 실제 게시/프로세스 재시작과 다르다. 새 작업 환경에 적용하려면 환경 설정에서 검토·저장 후 게시한다.

## 다음 구현 우선순위

1. WBS 4.2/4.5의 재개 정책·단계 projection/완전성 및 정식 v1 계약을 확정·연결한다.
2. 5.1/5.3 전체 오류/인증 응답·전 서비스 감사/보존과 6.1 트래픽 제외를 보완한다.
3. 실제 offset waterfall·원인 상세/배포 이력과 브라우저 검증, 전체 scrape/경보를 연결한다.
4. 운영 Secret/TLS/CI 취약점 검사·호환 앱 rollback·공간 부족/장기 부하를 실연한다.
5. 요구/담당자 확정 후 실제 차세대 업무 1건을 적용하고 G0~G5 인수를 진행한다.
