# 관측 계약 v1.0 — 개발 기준 초안

WBS 1.2 산출물. 생산 API/DB는 아직 이 계약을 구현하지 않는다. 기존 TraceEventVo/테이블에 필드를 일괄 추가하는 작업은 WBS 4번이다. 범위/운영 결정은 [1.1 문서](../../planning/stage1/SCOPE_AND_ACCEPTANCE.md)를 따른다.

## ID·전파 규칙

- traceId: W3C 32자리 소문자 hex, 모두 0 금지. spanId/parentSpanId: 16자리 소문자 hex, 모두 0 금지. 유효한 traceparent 우선.
- processId: 업무 단위 불투명 문자열(1~100자), 기존 SIGNUP-*도 유효. 다른 실행/Trace가 동일 processId에 연결될 수 있다.
- stepId: 정의상 단계 코드. stepExecutionId: 단계 실행 UUID. 동일 단계의 재시도는 새 실행 ID와 증가한 attempt로 구분한다.
- eventId/errorId: UUID. 같은 이벤트 재전송은 eventId 유지. ID만으로 권한/테넌트를 신뢰하지 않는다.
- instanceId: 서비스 시작 시 새 실행 ID. environment/serviceName/deploymentVersion과 함께 기록한다.
- 외부 X-Trace-Id만 유효한 32자 형식이면 표준 생성기의 검증·추출 정책을 적용한다. 16자 legacy ID는 새 표준 ID와 legacyTraceId로 연결한다. traceparent와 충돌하면 traceparent 우선이며 legacy 상관관계만 남긴다.
- Servlet/Feign/Reactor/executor에서 문맥을 전달한다. 임의 ThreadLocal만으로 비동기 흐름을 연결하지 않는다. business key는 허용·마스킹한 값만 보낸다.

## 이벤트 envelope

[JSON Schema](observation-event.schema.json)는 **발행자 payload**를 검증한다. 필수 envelope는 schemaVersion=1.0, eventId, eventType, occurredAt(UTC Z), environment, serviceName, instanceId, deploymentVersion이다. receivedAt은 인증된 수집기가 저장 시 부여하며 발행자는 전송하지 않는다. 필드 크기·enum·금지된 추가 필드를 제한한다. schema만으로 마스킹·인증·원자 저장·이벤트 순서를 보장하지 않는다.

| 이벤트 | 추가 필수 필드 | 의미 |
|---|---|---|
| PROCESS_STARTED | processId/processType/processDefinitionVersion/aggregateVersion/traceId/spanId/status | RUNNING 최초 시작 |
| PROCESS_FINISHED | 위와 동일 + durationMs | 프로세스 종료 상태 |
| STEP_STARTED | 위 + stepId/stepExecutionId/attempt | 단계 실행 RUNNING |
| STEP_FINISHED | 단계 필드 + durationMs | 단계 종료. 미실행 SKIPPED/CANCELLED는 durationMs=0 가능 |
| ERROR_RECORDED | traceId/spanId/errorId/errorCode/errorCategory/retryable | 요청 오류. background 오류는 별도 후속 계약으로 정의 |

occurredAt은 발행 시각, durationMs는 단조 시계로 계산한 0 이상의 시간이다. aggregateVersion은 같은 process 원본이 부여하는 증가 버전으로 여러 단계에 공통 적용한다. 동시성 충돌은 DB version/제약으로 제어한다. 전송 순서와 발생 시각만으로 원본 상태를 덮어쓰지 않는다.

이 계약은 업무 이벤트/요청 오류용이다. 기술 span의 상세 wire 형식은 OTLP, 서비스 probe 관측은 별도 health 계약으로 구현 시 분리한다. 임의 서비스 상태를 이 schema의 status에 넣지 않는다.

## 상태 전이

| 대상 | 시작/대기 | 종료 | 규칙 |
|---|---|---|---|
| Process | RUNNING | SUCCEEDED, REJECTED, FAILED, TIMED_OUT, CANCELLED | 종료 후 같은 실행은 RUNNING 복귀 금지 |
| Step | PENDING → RUNNING | SUCCEEDED, REJECTED, FAILED, TIMED_OUT, SKIPPED, CANCELLED | PENDING→SKIPPED/CANCELLED 허용, 실행 시작/종료는 다른 eventId |

PENDING은 원본/조회 모델의 대기 상태이며 이 버전의 STARTED 이벤트는 RUNNING만 허용한다. 재개는 별도 시도/실행 이력을 추가하고 이미 완료된 단계를 임의 덮어쓰지 않는다. 실행 중 프로세스의 retry 정책과 종료된 프로세스의 재실행은 구분한다. 종료 이벤트 누락은 중앙 completeness=INCOMPLETE이며 업무 실패로 자동 변환하지 않는다.

## 중복·순서·버전

- 수집 유일 키: (environment, serviceName, eventId). 같은 ID/동일 payload는 재승인, 같은 ID/다른 payload는 충돌로 거부한다. 서버 enrichment는 발행 payload 비교에서 제외한다.
- 중앙 영속화 후에만 항목별 승인. ACK 유실 후 재전송 허용. exactly-once 전송/업무 처리를 주장하지 않는다.
- 낮은 aggregateVersion 이벤트도 이력에는 보관하되 최신 projection을 역행시키지 않는다. 버전 누락은 보류·원본 대조로 처리한다.
- 1.0은 추가 필드도 거부한다. 확장은 별도 schemaVersion과 대응 schema를 먼저 배포한다. 알 수 없는 버전/유형은 항목별 격리하고 전체 batch를 무한 재시도하지 않는다.
- JSON Schema는 한 이벤트 구조만 검증한다. 중복/상태 전이/순서/마스킹/권한은 별도의 저장·통합 시험 대상이다.

## 오류 코드와 응답

| category | 대표 코드 | HTTP / 업무 결과 | retryable |
|---|---|---|---|
| BUSINESS | HR_NOT_ACTIVE, MEMBER_ALREADY_EXISTS | 422/409 · REJECTED | false |
| VALIDATION | INVALID_REQUEST | 400 | false |
| AUTHENTICATION | AUTH_REQUIRED, TOKEN_INVALID | 401 | false |
| AUTHORIZATION | ACCESS_DENIED | 403 | false |
| DEPENDENCY | HR_UNAVAILABLE | 503 · FAILED | 정책에 따라 |
| TIMEOUT | HR_TIMEOUT | 504 · TIMED_OUT | 정책에 따라 |
| DATA | DATA_WRITE_FAILED | 500 · FAILED | 원인/중복 방지에 따라 |
| INTERNAL | INTERNAL_ERROR | 500 · FAILED | 기본 false |

기존 AUTHENTICATION/AUTHORIZATION은 의미 유지, CONNECTION/CIRCUIT_BREAKER/EXTERNAL_SYSTEM은 DEPENDENCY로 정규화하고 세부 원인을 별도 보관, DATABASE는 DATA로 정규화한다. 기존 SUCCESS→SUCCEEDED, WAITING→PENDING, TIMEOUT→TIMED_OUT, NOT_EXECUTED→SKIPPED. 기존 FAILED는 근거가 있을 때만 REJECTED/FAILED로 세분하고 과거 값을 추정 변경하지 않는다. 미해석 값/ID는 legacy로 표시한다.

외부 응답 제안: `code`, 안전한 `message`, `errorId`, `traceId`. 내부 stack/SQL/비밀번호/토큰은 제외한다. 오류 그룹은 서비스·정규화 코드·예외 유형·코드 위치로 만들고 사용자 입력을 키에 넣지 않는다. causedByErrorId는 원인이 확인된 경우에만 연결한다. 오류 빈도·가용성 판단은 업무 거부와 기술 실패를 분리한다.

## 검증

`python tools/verification/validate_contract.py`는 schema와 정상/오류 예제를 검증한다. Python jsonschema 패키지가 필요하다. 이는 계약 예제 검증이며 현재 서비스가 v1을 송수신한다는 증거는 아니다. 운영 보안·부하·버전 이행 검증은 후속 WBS에 남는다.
