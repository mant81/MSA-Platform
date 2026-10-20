# WBS 4.2 — 승인 기반 가입 요청 재시도

기준일: 2026-10-20 (Asia/Seoul). 기준 소스는 main `e0796e5`이며 후속 변경은 현재 작업 디렉터리에 있다. 회원가입 샘플·단일 VM·H2/PostgreSQL 범위다. Redis·Kafka 및 범용 workflow/Saga를 추가하지 않았다.

## 구현과 판정

**로컬 원자 업무가 중단된 후 관리자가 재시도를 허용하는 개발 기능을 구현·검증했다.** 운영 인수·외부 쓰기 보상·임의 중간 단계 재개를 완료한 의미는 아니다. WBS 4.2의 기존 RECOVERY_REQUIRED 표시에서 승인/재시도까지 연결했다.

- V5 migration: claim의 owner/member/initialTrace/retryCount, 복구 감사 테이블·인덱스. 이전 migration을 수정하지 않는다. 과거 행의 미확인 식별자를 자동 추정하지 않는다.
- OPERATIONS/ADMIN 복구 대기/cursor 조회 및 감사 조회, ADMIN 전용 allow-retry API. Gateway와 backend가 같은 권한 경계를 적용한다.
- RECOVERY_REQUIRED→RETRY_ALLOWED 승인과 감사는 같은 DB 트랜잭션으로 저장한다. 요청 행 잠금으로 중복 승인을 거부한다. 승인은 가입 업무를 직접 실행하지 않는다.
- 원래 사용자가 같은 키·같은 입력을 보내면 잠금 아래 RUNNING 전이·retryCount 증가·RETRY_STARTED 감사가 commit된다. 여러 동시 재요청이 같은 작업을 실행하지 않는다. 종료 후 재요청은 저장된 응답을 반환한다.
- 기존 업무·단계·Outbox·응답의 원자성은 유지한다. DB 오류는 rollback 후 RECOVERY_REQUIRED로 돌아간다. 승인된 재시도도 동일한 업무 경로·Trace·Outbox를 사용한다.
- RUNNING/완료 응답/기존 회원/기존 성공 프로세스/식별자 없는 과거 행/재시도 3회 소모는 승인하지 않는다. 입력 변경은 승인을 소비하지 않고 409다.
- 최초 Trace와 승인/재시도 Trace를 보관한다. 감사 사유는 DB_ROLLBACK_CONFIRMED 또는 PROCESS_CRASH_CONFIRMED로 제한하며 자유 텍스트·원본 키·fingerprint·응답 JSON을 조회에 내보내지 않는다.

## 검증

| 구분 | 최종 결과 | 증적 |
|---|---|---|
| Java 25 전체 build/JUnit | 43건, 실패/오류/skip 0 | [요약](evidence/test-summary.json) |
| H2 실제 Gateway/UI HTTP 회귀 | 137체크 PASS | [H2](evidence/h2-report.json), run `20261020T123542Z-8765d1` |
| PostgreSQL 실제 Gateway/UI HTTP 회귀 | 145체크 PASS | [PostgreSQL](evidence/postgres-report.json), run `20261020T123453Z-09ebe5` |
| PG 백업 복원·재시작·migration | 4체크 PASS | [DB 증적](evidence/database-report.json) |

HTTP 수에는 환경별 시작 확인 7건과 기존 보안·추적 회귀를 포함한다. H2에는 복구 API/RBAC 5체크, PostgreSQL에는 해당 5체크 및 실제 재시도/부작용/감사 7체크를 추가했다. 두 DB의 반복 검사를 고유 기능 수로 합산하지 않는다.

단위/통합 시험은 실제 DB 저장 실패 후 rollback·재개, 다른 입력 거부, 승인만으로 회원 미생성, 동시 승인 2건 중 한 건 성공, 동시 재시도 6건의 단일 회원/프로세스, 횟수 한 번 증가, 완료/진행/과거/한도/기존 부작용 차단, 감사 저장 실패 시 승인 rollback, 최초 Trace 유지·민감 필드 미노출을 검증했다.

PG 회귀는 ownership label이 일치하는 자체 임시 컨테이너에만 중단 claim fixture를 삽입한 뒤 **실제 Gateway API**로 승인·재시도·조회했다. DB 실패/프로세스 중단의 상태를 재현하는 fixture이며, 이 항목을 물리적 crash의 모든 commit 경계 시험으로 주장하지 않는다. 기존 Outbox 시험의 Member JVM 재시작은 별도 실제 실행으로 유지했다. 임시 DB 비밀번호와 서명 키는 출력하지 않았고 own container만 종료/삭제했다.

## 운영 절차와 제한

[운영 API](../../operations/API.md) · [복구 runbook](../../operations/RUNBOOK.md) · [WBS](../MSA_ADOPTION_WBS.md) · [CSV](../MSA_ADOPTION_WBS.csv).

1. 운영자는 복구 대기 및 최초 Trace, 기존 업무 효과, 진행 중 트랜잭션·DB 복원 이력을 확인한다.
2. ADMIN이 허용 사유 코드로 재시도를 승인한다. 동시/중복 승인은 409다.
3. 원래 사용자가 원래 키·동일 입력으로 가입 요청을 보낸다. 키를 바꾸거나 claim을 삭제하지 않는다.
4. 감사·성공 업무 한 건·Outbox와 중앙 Trace를 확인한다.

이 샘플은 HR 읽기와 Member의 단일 DB 트랜잭션을 사용하므로 rollback 후 처음부터 재실행한다. 결제·메일·외부 저장 등 부작용이 추가되면 현재 정책으로 재개를 허용하기 전에 보상/중복 효과 확인을 설계해야 한다. 승인 TTL, 실제 담당자·역할 프로비저닝·감사 보존·알림과 운영 DB 과거 데이터 이행은 별도 인수 대상이다. UI에 복구 관리 패널은 아직 추가하지 않았으며 API/runbook으로 제공한다.

재현은 기존 Java/proxy 초기화 후 다음을 사용한다.

```bash
./gradlew -I /workspace/cloud-setup/proxy.gradle --project-cache-dir build/verification/gradle-cache --no-daemon --max-workers=2 build
python3 tools/verification/stage1_baseline.py --next --java "$JAVA_HOME/bin/java"
python3 tools/verification/postgres_application_drill.py
python3 tools/verification/check_generated_secrets.py
```

기존 후속 결과는 당시 증적으로 유지한다. 다음 우선 구현은 WBS 4.5의 단계 projection·완전성 및 정식 이벤트 계약 연결이다. 전체 운영 Gate는 기존 미확정 항목이 있어 보류한다.

후속 WBS 4.5 구현 결과는 [단계 projection 검증](../flow/RESULTS.md)에 기록한다. 위 복구 증적은 당시 실행 결과로 유지한다.
