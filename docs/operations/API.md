# 운영 API와 호환 계약

외부 경유: Gateway. 운영 조회는 OPERATIONS/ADMIN, 정비 변경은 ADMIN이다. 내부 backend는 사용자 JWT와 수신 대상별 서비스 자격을 독립 확인한다. Trace 수집은 허용된 내부 writer 전용이다.

| Method/path | 용도 |
|---|---|
| GET `/members/processes/**` | OPERATIONS/ADMIN 전용 프로세스·단계·timeline 조회 |
| POST `/members` | ADMIN 전용 단순 데이터 관리; 일반 사용자는 가입 workflow 사용 |
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
