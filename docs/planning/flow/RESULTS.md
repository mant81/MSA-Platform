# WBS 4.5 — 단계 projection·기간 조회·완전성 사유

기준: 2026-10-20, main e0796e5 이후 작업 디렉터리. 회원가입 샘플·단일 VM 범위다.

## 구현

- V8 migration으로 단계 실행별 projection을 추가했다. 기존 Member 단계 이벤트를 이행하며, 종료 이벤트가 있으면 늦게 도착한 시작 이벤트보다 우선한다. 과거 생성 시각이 없는 경우 이행 시각을 사용한다. 원본 이벤트는 변경하지 않는다.
- source/process/execution ID로 시도를 분리하고 단계 ID·시도·Trace/Span·상태·최종 버전을 조회한다. 중복 이벤트는 기존 dedup을 사용하고 원본 저장/projection 갱신은 한 트랜잭션이다. 종료 후 상태는 역행하지 않는다. 상충하는 종료 상태를 자동 재판정하는 기능은 없다.
- Gateway `GET /operations/flows`: 이벤트 cursor·최대 200건·businessKey/업무/기간 검색, 반개구간 since/until와 역전 경계 400. 단계 projection도 최대 200건이며 초과를 표시한다. 운영 UI에 단계·시도·상태와 완전성 사유를 연결했다.
- 이벤트 페이지와 projection 범위를 명시적으로 구분한다. projection은 전체 수집 상태이므로 원본 보존 삭제 후에도 남을 수 있다. 페이지 밖의 이력을 전체 누락으로 오판하지 않는다.
- 현재 aggregateVersion은 연속 발행 번호가 아니다. 발행 측 예상 이벤트 manifest가 없어 완전성은 UNVERIFIED 및 구체적 사유로 제공한다. 종료/4개 단계 존재만으로 COMPLETE를 선언하지 않는다. Gateway/HR의 best-effort 진단 이력 전체 수집도 보장하지 않는다.

## 검증

| 검증 | 최종 결과 | 증적 |
|---|---|---|
| Java 25 전체 build/JUnit | 46건, 실패·오류·skip 0 | [JUnit](evidence/test-summary.json) |
| H2 Gateway HTTP | 141 PASS | [H2](evidence/h2-report.json), run 20261020T130234Z-649e22 |
| PostgreSQL Gateway HTTP | 149 PASS | [PG](evidence/postgres-report.json), run 20261020T130324Z-7a2c89 |
| PostgreSQL 복원·재시작·migration | 4 PASS | [DB](evidence/database-report.json) |
| 운영 UI DOM fixture | PASS | `node tools/verification/operations_ui_test.cjs` |

증적의 jar SHA-256을 최종 build 산출물과 대조했다. HTTP 수는 기존 보안/복구 회귀·서비스 시작 체크를 포함하며 고유 기능 수가 아니다.

 JUnit은 역순·중복·TIMEOUT·재시도 분리·타 서비스 격리·기간 경계 및 V7→V8 기존 행 이행/반복 적용을 확인한다. 실제 HTTP 회귀는 Gateway 권한, 네 단계 projection과 cursor 페이지의 독립성, 완전성 사유, 기간 경계/400, 기존 복구·Outbox 기능을 함께 확인한다. UI는 Node DOM fixture로 표시와 안전한 text 렌더링을 확인하며 실제 브라우저 인수는 별도다.

첫 병렬 통합 실행은 HR/HTTP 시간 초과로 실패했다. 해당 실행은 ignored build 증적으로 보존하며 최종 빌드로 직렬 재검증했다. 실패 실행 ID: H2 20261020T130102Z-2160ab, PG 20261020T130113Z-de9b4d. 자원 경합은 추정이며 운영 용량 검증 결과가 아니다.

## 후속 및 인수 범위

WBS 4.5의 단계·기간·불완전성 표시를 개발 검증한다. 정식 이벤트 계약/예상 manifest에 근거한 COMPLETE 판정, 충돌 이벤트 탐지·재구축 절차·projection 보존 정책, 실제 업무/운영 인수는 남아 있다. 기존 미확정 SLO/RTO/RPO와 운영 Gate를 완료 처리하지 않는다. Redis/Kafka는 도입하지 않았다.

[WBS](../MSA_ADOPTION_WBS.md) · [API](../../operations/API.md) · [운영 절차](../../operations/RUNBOOK.md)
