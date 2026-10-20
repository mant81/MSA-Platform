# WBS 2단계 — 기존 통합 흐름 복구

기준일: 2026-10-20 (Asia/Seoul). 적용 범위는 사용자 확인한 회원가입 샘플·단일 VM의 개발 검증이다. 기존 WBS 1단계의 운영 미확정 항목은 유지한다.

## 구현

| WBS | 변경 | 완료 확인 방법 |
|---|---|---|
| 2.1 | Gateway의 block() 제거, 256개 버퍼/단일 sender의 비동기 전송, 전송별 500ms 제한, 수집 실패/포화 로그, 종료 시 sender 정리 | Gateway 필터 단위 회귀와 실제 인증 요청·수집기 오류 시험 |
| 2.1 | 인증 필터보다 먼저 Trace ID 부여, 응답 헤더 단일화, 4xx/5xx/라우팅 예외 실패 기록, 오류 응답에서도 같은 ID 유지 | 401/422/503/504 중앙 기록과 응답 헤더 비교 |
| 2.2 | HR active 계약 사용, 요청 Trace ID 재사용·Feign 전달·HR 응답 헤더 유지 | UI→Gateway→Member→HR 실제 호출과 HR 경계 관측 |
| 2.2 | 업무 거부/HR Timeout/HR 연결·5xx 오류 구분, 현재 단계 실패·후속 단계 미실행·프로세스 종료, 단계 소요 시간 기록 | 정상/비활성/미등록·503·지연·연결 종료 후 DB 상태 조회 |
| 2.3 | UI의 Authorization/Trace 문맥 전달, Gateway 오류 상태·본문 및 Trace 응답 헤더 보존, JS 공통 인증 헤더 적용 | UI 경유 가입·조회·401/422 오류 응답 확인 |
| 2.3 | Gateway의 GET 전용 Trace route, Auth/Config/Member 기본 포트 8082/8081/8083 설정 | UI→Gateway→Trace 조회, 외부 POST 미라우팅, 설정 대조 |

가입 응답은 기존 MemberSignupVo를 유지하고 errorCode/errorType/message를 추가했다. 새 DTO 계층이나 Redis/Kafka는 추가하지 않았다.

## 응답·상태 규칙

| 상황 | HTTP / errorCode | 프로세스·현재 단계 |
|---|---|---|
| ACTIVE 직원 정상 가입 | 200 / 없음 | SUCCESS / SUCCESS |
| INACTIVE·미등록 직원 | 422 / HR_NOT_ACTIVE | FAILED / FAILED, BUSINESS |
| HR 5xx·연결 실패 | 503 / HR_UNAVAILABLE | FAILED / FAILED, CONNECTION |
| HR 응답 Timeout | 504 / HR_TIMEOUT | FAILED / TIMEOUT |
| 기존 샘플 외부 Timeout 시뮬레이션 | 504 / EXT_TIMEOUT | FAILED / TIMEOUT |

후속 미실행 단계는 기존 NOT_EXECUTED로 기록한다. v1 계약의 SUCCEEDED/REJECTED/TIMED_OUT/SKIPPED로 DB 전체를 이행하는 것은 WBS 4번이며 이번 단계의 레거시 상태를 새 계약 완료로 보지 않는다.

## 재현

```bash
./gradlew --project-cache-dir build/verification/gradle-cache --no-daemon --max-workers=2 build
python tools/verification/stage1_baseline.py --stage2 --java "$JAVA_HOME/bin/java"
```

현재 클라우드에서는 `/workspace/cloud-setup/env.sh` 활성화 및 Gradle의 `-I /workspace/cloud-setup/proxy.gradle`이 필요하다. `stage1_baseline.py`는 환경 준비·정리를 공유하고 `stage2_regression.py`는 새 회귀 기준을 실행한다. stage1의 과거 판정/보고서는 변경하지 않는다.

시험은 임시 포트/H2/자기 자식 프로세스로 격리하며 실제 운영 데이터에 요청하지 않는다. 표준 출력·JSON의 PASS/FAIL/BLOCKED/ERROR와 종료 코드를 따른다. 7개 서비스의 START 체크는 기능 시험과 구분한다. 실패한 중간 실행은 성공 증적으로 교체해 해석하지 않으며 최종 실행 ID를 명시한다.

## 남은 경계

- 샘플 토큰·평문 비밀번호·미구현 RBAC/Refresh는 WBS 3번 대상이다. Trace GET은 샘플 인증만 요구하며 운영 역할별 권한은 아직 없다. 운영 배포 적합성을 주장하지 않는다.
- Gateway 버퍼는 메모리 best-effort다. 포화·종료 시 이벤트 유실 가능성이 있고 실패/유실 카운터는 로그에 남긴다. 내구 Outbox·재전송·메트릭 노출은 WBS 4/6번이다.
- Member Trace HTTP는 실패가 업무 예외를 덮지 않도록 제한 시간/오류 격리를 추가했지만 여전히 동기다. 여러 기록의 지연이 합산될 수 있다. Auth/Config의 수집기 장애 격리도 후속 범위다. 이번 수집기 오류 시험은 Gateway 수집 경로만 중단하고 하위 서비스 수집 경로는 유지했다.
- 업무 DB 자체가 중단되면 원본 실패 상태 기록도 실패할 수 있다. DB 장애·원자성·재시작 복구·중복 방지·장기 RUNNING 보정은 WBS 4/9번이다.
- X-Trace-Id의 기본 연결을 복구한 단계다. W3C span 부모 관계, 서비스별 기술 span의 완전한 수집은 WBS 4.3에서 구현한다. HR 헤더 확인은 HR 내부 span 저장 증거가 아니다.
- 휴면/외부 적격성 단계는 기존 시뮬레이션이다. 실제 외부 시스템 연계나 신규 차세대 업무 인수로 계산하지 않는다.

## 최종 검증 결과

- Java 25 전체 build 성공. Gateway 단위 회귀 6건 + HR HTTP/H2 통합 3건 = 9건, 실패/오류/skip 0.
- 격리 HTTP 회귀: 40건 PASS, FAIL/BLOCKED/ERROR 0. 이 중 서비스 시작 확인 7건과 기능 체크 33건을 구분한다.
- 최종 실행 ID: `20261020T002107Z-48a23d`, 종료 코드 0. [회귀 증적](evidence/regression-report.json) · [JUnit 요약](evidence/test-summary.json).
- 이전 2개 실행은 시험 설정 중복과 단계 갱신 SQL 오류로 실패했다. 해당 문제를 수정한 뒤 위 최종 빌드/회귀가 통과했다. 실패 실행 로그는 ignored build/verification/stage2에 남긴다.
- 임시 시험 프로세스는 종료했으며 기존 수동 실행 프로세스나 외부 환경을 재배포한 것으로 주장하지 않는다.

WBS 2.1~2.3은 **회원가입 샘플의 개발 검증 완료**로 표시한다. G1은 인증/인가 보강(WBS 3번)이 남아 미충족이며 전체 운영 도입 판정은 보류다. 실제 운영 목표·인수자 미확정(G0)도 유지한다. 다음 구현은 WBS 3번 인증·보안 기반이다.
