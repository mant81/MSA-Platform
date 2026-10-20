# 1.3 검증 환경·장애 주입·판정표

상태: 로컬 격리 환경과 재현 도구 준비. 운영 유사 검증 환경 전체 인수는 미완료다. 범위는 [도입 기준](SCOPE_AND_ACCEPTANCE.md)을 따른다.

## 환경과 재실행

필수: Java 25, Python 3.10 이상, Docker daemon(PostgreSQL drill에 한함), 저장소 wrapper와 빌드 의존성 접근. 실제 운영 DB/망/HA는 아직 확정되지 않았다.

저장소 루트에서 실행한다. 기존 추적된 서비스별 .gradle 캐시에 쓰지 않도록 루트 build/ 아래의 별도 캐시를 사용한다.

```bash
./gradlew --project-cache-dir build/verification/gradle-cache --no-daemon --max-workers=2 build
python -m venv build/verification/venv
build/verification/venv/bin/pip install -r tools/verification/requirements.txt
build/verification/venv/bin/python tools/verification/validate_contract.py
python tools/verification/stage1_baseline.py --java "$JAVA_HOME/bin/java"
python tools/verification/postgres_drill.py
```

클라우드 프록시가 필요한 현재 환경에서는 기존 `/workspace/cloud-setup/env.sh`를 활성화하고 Gradle 명령에 `-I /workspace/cloud-setup/proxy.gradle`을 추가한다. 이 환경 전용 초기화는 서비스의 운영 필수 의존성이 아니다.

명령은 각각 실행하고 종료 코드를 보존한다. baseline이 실패해도 독립적인 계약/DB drill은 수행하되, 이를 전체 통합 통과로 합산하지 않는다. 기존 `.gradle` 캐시를 수정하거나 이미 실행 중인 서비스를 중단하지 않는다.

## 도구와 수명

| 도구 | 실행 대상 | 정리/출력 | 범위 |
|---|---|---|---|
| validate_contract.py | JSON Schema v1 및 정상/오류 fixture | 표준 출력, 서비스 기동 없음 | 구조/enum/ID/필수 필드 검증 |
| stage1_baseline.py | 별도 임시 포트의 서비스 7개 + HR fault proxy | 자기 자식 프로세스만 종료, build/verification/stage1/<runId>/report.json·로그 | H2 기반 현재 동작과 장애 재현 |
| postgres_drill.py | digest 고정 PostgreSQL 18 임시 컨테이너 | 자기 컨테이너·anonymous volume 삭제, build/verification/stage1/msa-stage1-pg-*/report.json | Trace 스키마·DB 중단/재시작 drill |

- baseline은 실행 직전 전체 build로 JAR를 갱신해야 한다. 소스/JAR 지문을 기록하지만 JAR가 현재 소스에서 빌드됐다는 사실을 지문만으로 증명하지는 않는다.
- baseline의 데이터는 새 H2 메모리 DB만 사용한다. 직원 샘플은 각 서비스 schema.sql에서 초기화한다. 기존 8080대 서비스에는 요청하지 않는다.
- 테스트 인증 헤더는 현재 저장소 샘플 형식을 재현할 뿐 실제 서명 검증을 대신하지 않는다. WBS 3.2 이후 실제 로그인 기반으로 교체한다.
- Fault proxy는 정상 전달/HTTP 503/1초 지연/연결 종료를 제공한다. Member의 시험용 Feign connect/read timeout은 250ms로 덮어쓴다. 이것은 운영 정책 반영이 아니다.
- PostgreSQL 컨테이너는 외부 포트를 열지 않고 network=none이다. trust 인증은 외부 접근이 없는 임시 fixture에 한정한다. 실제 운영 인증/TLS는 검증하지 않는다.
- DB drill은 기존 Trace schema.sql만 직접 적용한다. Member/Auth/Config의 PostgreSQL JDBC 전체 통합, 운영 migration, 백업 복원, HA/RTO/RPO를 검증한 것으로 보지 않는다.
- Member/Auth/Config의 비H2 설정은 주소·계정을 고정하는 현행 제약이 있어 WBS 4.1의 runtime 설정 외부화에 포함한다.

## 종료 코드

- 계약 validator: 0=예제 검증 성공, 그 외=계약 또는 도구 오류.
- 서비스 baseline: 0=수행 체크 모두 통과, 1=환경/실행 오류, **2=기능 실패 또는 Gateway 경유 차단**. 0이어도 이 범위만으로 운영 적합성이 확정되지 않는다.
- PostgreSQL drill: 0=수행 DB 체크 통과, 1=실패. PASS는 운영 복구 목표 달성이 아니다.

## 판정표

| 시나리오 | 기대 결과 | 현재 상태와 후속 |
|---|---|---|
| 계약 정상/오류 예제 | 정상 수용·잘못된 ID/필수 필드/상태/미지원 버전 거부 | validator로 실행 |
| 서비스 시작 | 독립 포트의 HTTP 응답 확인 | baseline으로 실행 |
| Gateway 무인증 요청 | 401 | baseline으로 실행 |
| Gateway 인증 요청 | 200 및 동일한 Trace 문맥 | block() 오류로 FAIL; WBS 2.1 |
| Gateway→Member→HR 가입 | 정상 직원 성공, 비활성/미등록 거부 | 인증 경로 실패면 BLOCKED; 우회 진단을 통합 성공으로 계산하지 않음 |
| 직접 Member ACTIVE 진단 | 회원 저장·정상 결과 | HR active/exists 불일치로 FAIL; WBS 2.2 |
| 직접 Member INACTIVE 진단 | 현행 FAILED 응답 | legacy 거부 경로만 확인. v1 REJECTED/오류 응답 준수는 미검증 |
| HR 503/지연/연결 종료 | 오류 분류·deadline·종료 상태 | fixture 작동과 업무 처리를 따로 판정; RUNNING 잔류는 FAIL |
| HR fixture 복구 | 정상 HR 응답 재개 | 업무 재개/보상 처리 성공으로 해석하지 않음 |
| Trace 목록·UI HTML | 정상 JSON·HTML 응답 | 기록 조회/화면 기동만 검증, 표준 span·권한·전체 UI 흐름은 미검증 |
| PostgreSQL Trace 저장/조회 | 스키마 적용 및 한 건 조회 | DB drill로 실행 |
| PostgreSQL 중단/재시작 | 중단 시 접근 불가, 재시작 후 같은 데이터 | DB drill로 실행, 백업 복원/앱 DB 장애 처리는 별도 |
| 이벤트 중복·역순/Outbox 복구 | 업무 원본과 projection 정합 | 미실행·기능 미구현, WBS 4.2~4.5/9.2 |
| 서비스 DOWN/UNKNOWN 판정 | 대상 장애·수집기 장애 구분 | 미실행·기능 미구현, WBS 6번/9.2 |
| 운영 권한/부하/백업 복구 | 합의 운영 기준 충족 | 미실행·기준 미확정, WBS 3/8/9번 |

실행 결과의 PASS/FAIL/BLOCKED/ERROR와 미실행 항목을 구분한다. 정상 직원 하나의 실패가 전체 서비스 DOWN이라는 뜻은 아니다. 재현한 알려진 기능 실패도 PASS 또는 expected-success로 바꾸지 않는다.

## 증적 관리

report.json에 run ID·Java 버전·포트·JAR/소스 SHA-256·체크별 결과를 남긴다. 서비스 로그는 생성 결과물로 build/에 보관한다. 재현 가능한 결과 요약과 비밀 없는 report는 [1단계 실행 결과](RESULTS.md)에 연결해 저장한다. 운영 로그 이관 시 개인정보·토큰 제거와 접근 권한을 별도로 검토한다.
