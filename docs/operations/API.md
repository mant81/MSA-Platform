# 운영 API와 호환 계약

외부 경유: Gateway. 운영 조회는 OPERATIONS/ADMIN, 정비 변경은 ADMIN이다. 내부 backend는 사용자 JWT와 수신 대상별 서비스 자격을 독립 확인한다. Trace 수집은 허용된 내부 writer 전용이다.

| Method/path | 용도 |
|---|---|
| GET `/members/processes/**` | OPERATIONS/ADMIN 전용 프로세스·단계·timeline 조회 |
| POST `/members` | ADMIN 전용 단순 데이터 관리; 일반 사용자는 가입 workflow 사용 |
| GET `/members/requests?cursor=...` | OPERATIONS/ADMIN, 복구·재시도 대기 상태 최대 100건; 다음 cursor는 마지막 requestKey |
| GET `/members/requests/{requestKey}/audit` | OPERATIONS/ADMIN, 승인/재시도 감사 최대 100건 |
| POST `/members/requests/{requestKey}/allow-retry` | ADMIN, 허용 사유 코드로 로컬 재시도 승인; 업무를 직접 실행하지 않음 |
| POST `/members/signup` + `Idempotency-Key` | 사용자별 키·입력 해시, 원자적 가입 및 재조회 |
| POST `/trace-events` | 기존 단건 writer 호환 수집; sourceService는 인증 결과로 덮어씀 |
| POST `/trace-events/batch` | 1~100건, eventId 필수, eventId별 PERSISTED/DUPLICATE 승인 |
| GET `/trace-events/trace-id/{id}` | 표준 ID 또는 과거 legacy ID 조회 |
| GET `/operations/flows?cursor=0&limit=100&processId=...&businessKey=...&since=...` | 최대 200건, ingestion ID cursor, 기간 시작·업무 필터, terminal projection |
| GET `/operations/errors?since=...` | 기본 최근 1일·최대 100개 서비스/코드/배포 그룹 |
| GET `/operations/errors/{code}/impact?cursor=...` | 오류 코드의 영향 event·업무·Trace, 최대 100건 |
| GET `/operations/errors/{errorId}/causes` | root/전파 오류 연결, 최대 100건 |
| GET `/operations/services` | 인스턴스·서비스 집계·관측 시각·근거·수집기 상태 |
| GET `/operations/services/history` | 최신 판정 변경 200건 |
| GET `/operations/incidents` | 최신 incident 100건 |
| POST `/operations/incidents/{id}/ack` | 운영자 확인·중복 억제·감사 |
| POST `/operations/services/{id}/maintenance?enabled=true` | ADMIN, 1시간 새 incident 억제·감사 |
| GET `/operations/alerts` | 외부 수신처 설정 여부·미전송 수·at-least-once 보장 |
| GET `/actuator/health/liveness`, `/readiness` | 최소 상태만 공개, DB 세부/자격 미노출 |
| GET `/actuator/prometheus` | JWT 보호 지표; backend 직접 요청에는 서비스 인증 추가 |

배치에 일부 이벤트가 저장된 후 실패할 수 있다. 승인되지 않은 전체 묶음을 재전송하면 unique event ID로 이미 저장된 항목은 DUPLICATE 승인된다. 출처별 event ID는 불변으로 사용한다. 반환된 event ID만 Outbox에서 승인하며 HTTP 200 하나만으로 전체 승인하지 않는다.

새 수집 payload는 기존 TraceEventVo의 **호환 확장 계약**이다. eventId/spanId/parentSpanId/legacyTraceId/aggregateVersion/stepExecutionId/attempt/processDefinitionVersion/errorId/causedByErrorId 및 환경/인스턴스/배포 정보를 추가한다. [1단계 이벤트 v1 초안](../contracts/v1/README.md)의 PROCESS_FINISHED/SUCCEEDED 형태를 이 API가 완전히 구현했다고 보지 않는다. 기존 REQUEST_COMPLETED/SUCCESS 등 legacy event/status가 유지된다. 정식 v1 수집 endpoint와 단계 상태 전이의 전체 정규화는 후속 계약 작업이다.

flow projection은 인증된 member-service의 프로세스 시작/종료 이벤트만 사용한다. aggregateVersion보다 과거 이벤트가 오거나 terminal 이후 START가 도착해도 역행하지 않는다. 단계 이력은 event 목록에서 조회하며 ingest 순서와 업무 발생 순서가 다를 수 있다. 누락·보존 만료·메모리 진단 유실을 증명할 수 없어 completeness는 UNVERIFIED다. 완전성 확정·기간 끝 조건·단계별 projection/재개는 후속이다.

운영 화면 `/operations`는 로그인→서비스/장애/오류 그룹→영향 업무→flow 조회를 제공한다. 토큰은 메모리만 사용하며 임의 HTML을 넣지 않는다. 현재 단계별 소요 시간 막대는 상대 비교이며 실제 시작 offset을 쓰는 전체 waterfall·원인 span 탐색·배포 이력 화면은 후속이다. 실제 브라우저 다운로드는 현 환경에서 cdn.playwright.dev의 정책 403으로 차단됐다. Node DOM 및 실제 HTTP 중계 검증과 브라우저 인수를 구분한다.

## WBS 4.2 로컬 재시도 계약

승인 본문은 `{ "reasonCode": "DB_ROLLBACK_CONFIRMED" }` 또는 `{ "reasonCode": "PROCESS_CRASH_CONFIRMED" }`다. 자유 텍스트·토큰·회원 이름을 감사에 수집하지 않는다. 상태 변경과 감사 insert는 같은 트랜잭션으로 처리한다. 동시 승인에는 한 건만 성공하고 다른 요청은 409다.

운영자는 RECOVERY_REQUIRED, 응답 미저장, owner/member 식별 가능, 기존 회원 및 성공 프로세스 없음, 재시도 3회 미만인 요청만 승인할 수 있다. 404는 요청 없음, 400은 키/사유 형식 오류, 409는 안전하게 승인할 수 없는 상태다. RUNNING/COMPLETED/승인 대기 중복/기존 부작용/과거 메타데이터 미보유 요청을 자동 초기화하지 않는다.

승인 결과 RETRY_ALLOWED는 실제 가입 성공을 뜻하지 않는다. 원래 사용자가 기존 POST /members/signup에 원래 Idempotency-Key와 동일한 입력을 보내야 한다. 입력 불일치는 409이며 승인을 소모하지 않는다. 실제 재시도 claim은 잠금 아래 RUNNING으로 전이하고 retryCount를 한 번 증가시키며 RETRY_STARTED 감사를 남긴다. 진행 중 중복은 409, 종료 후 동일 요청은 캐시된 원래 결과다.

응답에는 requestKey·ownerId·memberNo·initialTraceId·상태·횟수·시각만 포함한다. 원본 키, fingerprint, 응답 JSON은 조회에 노출하지 않는다. V5 이전 미완료 행의 owner/member 메타데이터는 추측해서 채우지 않는다. 초기 Trace와 승인/재시도 감사의 Trace를 함께 조회할 수 있다.

이 기능은 Member 로컬 트랜잭션 rollback 후 **처음부터 재실행**하는 샘플 정책이다. 외부 쓰기/결제/메일 등의 부작용이나 임의 중간 단계 재개·보상은 지원하지 않는다. 운영자가 DB를 수정해 잠금을 우회하거나 승인 횟수를 초기화하면 보장 범위를 벗어난다.

### Flow 단계 projection 및 기간 범위 (WBS 4.5)

`GET /operations/flows?processId=...&since=2026-10-01T00:00:00Z&until=2026-11-01T00:00:00Z&limit=100&cursor=0`

OPERATIONS/ADMIN 권한을 사용한다. `since` 포함, `until` 제외이며 역전/동일 경계는 400이다. 이벤트는 수집 ID 오름차순, 최대 200건이며 다음 페이지는 `nextCursor`/`hasMore`를 사용한다.

`steps`는 processId 지정 시 해당 업무의 단계 실행별 최신 상태다. `source_service/process_id/step_execution_id`로 분리하며 `step_id`, `attempt`, `trace_id`, `span_id`, `status`, `aggregate_version`를 제공한다. 상태는 RUNNING/SUCCEEDED/FAILED/TIMED_OUT/NOT_EXECUTED다. 실행이 종료되면 늦게 온 시작 이벤트로 되돌리지 않는다. Member 이외 서비스의 진단 이벤트는 Member 단계 상태를 바꾸지 않는다.

단계 목록은 최대 200건이다. 초과 시 `completenessDetails.stepsTruncated=true`다. 업무/단계 projection은 이벤트 페이지의 cursor·기간과 독립적인 전체 수집 상태이며 `projectionScope=all-ingested-events-for-process`로 명시한다. 원본 이력 보존 삭제 후에도 projection이 남을 수 있다.

`completeness=UNVERIFIED` 및 `completenessDetails.reason=EXPECTED_EVENT_MANIFEST_UNAVAILABLE`는 현재 발행 계약에 예상 이벤트 목록이 없음을 뜻한다. processId 미지정 시 사유는 PROCESS_NOT_SELECTED다. 종료 상태 또는 단계 4개 존재만으로 전체 Trace 수집 완료를 선언하지 않는다. aggregateVersion은 연속 이벤트 번호가 아니므로 번호 차이를 누락 건수로 사용하지 않는다. 운영 화면은 단계·시도·완전성 사유를 표시한다. UI relay의 기간 입력은 아직 제공하지 않으며 기간 검색은 Gateway API를 사용한다.
