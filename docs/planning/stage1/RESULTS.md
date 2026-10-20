# WBS 1단계 수행 결과

기준일: 2026-10-20 (Asia/Seoul). 작업 범위는 1.1~1.3이다. 업무 서비스 소스·DB 스키마·인증 동작은 이번 단계에서 변경하지 않았다. Redis·Kafka는 추가하지 않았다.

## 수행 상태

| WBS | 이번 산출물 | 상태 / 남은 조건 |
|---|---|---|
| 1.1 | [도입 범위·시험 목표·결정 목록](SCOPE_AND_ACCEPTANCE.md) | 회원가입 샘플·단일 VM 우선 검증 및 시험용 제안값 진행을 사용자 확인. 실제 운영 요구·인수자는 미확정 |
| 1.2 | [ID·상태·이벤트·오류 계약 v1](../../contracts/v1/README.md), JSON Schema·예제·validator | 개발 초안 및 구조 검증 완료. 프로젝트 요구 반영/확정과 실제 송수신 구현은 별도 |
| 1.3 | [격리 검증 환경·장애 주입·판정표](VERIFICATION.md), 실행 도구와 결과 지문 | 로컬 H2 서비스·HTTP 장애·PG DB drill 재현 완료. 운영 유사 배포·전체 JDBC·HA/복구/부하 인수는 미완료 |

사용자는 2026-10-20 회원가입 샘플·단일 VM으로 우선 검증하고, 미정인 운영 목표는 시험용 제안값으로 진행하도록 회신했다. 문서와 WBS에 반영했다. 이 결정은 우선 검증 범위의 확인이며 실제 운영 요구·인수자 확정 또는 G0 통과를 뜻하지 않는다. 현재 클라우드 격리 시험이 실제 대상 VM에 배포됐다는 의미도 아니다.

## 실행 결과

| 실행 | 실제 결과 | 판정 범위 |
|---|---|---|
| 이벤트 계약 validator | 정상 예제 5건 수용, 잘못된 예제 25건 거부; 새 venv 설치 후 재검증 통과 | payload 구조만 검증; 상태 순서·내구성·현행 API 지원은 검증하지 않음 |
| 서비스 baseline 최초/재실행 | 각각 PASS 16, FAIL 5, BLOCKED 1, ERROR 0; 종료 코드 2 | 애플리케이션 결함을 숨기지 않고 재현. 두 실행에서 항목별 판정 동일 |
| PostgreSQL 최초/재실행 drill | 각각 3건 PASS; 최종 서버 18.6; 종료 코드 0 | Trace DDL/저장/조회·중단 시 접근 불가·재시작 데이터 유지 |
| 정리 | 시험 자식 서비스 종료, 임시 PG 컨테이너/볼륨 삭제 | 기존 서비스·다른 컨테이너는 중단하지 않음 |

PASS 16건 중 7건은 각 서비스의 HTTP 기동 확인이다. 기능 테스트 16개 전체가 통과했다는 의미가 아니다. 전체 흐름·운영 보안·서비스 장애 판정의 통과로 계산하지 않는다.

### 확인한 결함

| 항목 | 실제 관측 | 후속 WBS |
|---|---|---|
| Gateway 인증 요청 | 보호 API /configs HTTP 500; 전체 가입 경로 차단 | 2.1 |
| 정상 직원 직접 진단 | HR active=true에도 Member는 HTTP 200/FAILED | 2.2 |
| HR HTTP 503 | Member HTTP 500, 최신 process RUNNING 잔류 | 2.2 및 4.6 |
| HR 응답 지연 | 시험 readTimeout 발생 후 HTTP 500, process RUNNING 잔류 | 2.2 및 4.6 |
| HR 연결 종료 | Member HTTP 500, process RUNNING 잔류 | 2.2 및 4.6 |

직접 Member 호출은 원인 분리를 위한 진단이며 Gateway-first 통합 성공으로 계산하지 않았다. INACTIVE의 기존 FAILED 응답 확인도 새로운 REJECTED/HTTP 오류 계약 준수를 보장하지 않는다. 장애 주입기가 정상 작동한 것과 애플리케이션이 장애를 정상 처리한 것을 구분했다.

## 재현 증적

- [최종 서비스 baseline JSON](evidence/baseline-report.json): 실행 ID, Java 버전, 포트, JAR/소스 지문, 항목별 결과.
- [최종 PostgreSQL JSON](evidence/postgres-report.json): 이미지 digest, 서버 버전, 체크별 결과, 정리·한계.
- 원본 로그는 저장소의 ignored `build/verification/stage1/<runId>/`에 보관한다. 최초 실행 ID는 `20261020T000550Z-8c07e4`, 최종 baseline ID는 `20261020T000646Z-d85e61`이다.
- 스크립트: `tools/verification/validate_contract.py`, `stage1_baseline.py`, `postgres_drill.py`.
- 예제·보고서에는 테스트 식별자만 사용했다. 운영 데이터·자격증명은 포함하지 않았다.

## 다음 작업과 도입 판정

개발 기준으로 다음 구현은 WBS 2.1 Gateway 비블로킹 처리, 2.2 가입 계약/실패 상태 복구, 2.3 UI·route 정합성이다. WBS 1단계에서 해당 수정을 대신 수행하지 않았다.

**1단계 산출물과 로컬 시험은 준비됐지만 G0는 미충족이며 차세대 프로젝트 즉시 도입 가능 판정은 유지 보류다.** 확인된 샘플·단일 VM 범위를 기준으로 계속 진행하되, 실제 운영 요구·인수자 확정, 대상 VM 준비, 후속 구현/인수 시험이 필요하다. 최초 공수 95~154 PD는 실제 완료율 또는 현재 잔여 공수로 환산하지 않았다.
