# WBS 3단계 — 인증·보안 기반

기준일: 2026-10-20 (Asia/Seoul). 회원가입 샘플·단일 VM의 개발 검증 범위다. 실제 운영 기준·인수자 미확정(G0), 운영 배포/복구/부하/전문 보안 인수는 유지한다. Redis·Kafka는 도입하지 않았다.

## 수행 상태

| WBS | 산출물 | 상태 |
|---|---|---|
| 3.1 | BCrypt 12, ID/비밀번호 검증, 조회 SQL 제외+JsonIgnore, 평문 fallback 금지, 중복 가입 유일 제약 | 샘플 개발 검증 완료 |
| 3.2 | Spring Security/Nimbus RS256, 외부 키, kid 공개키 집합, issuer/aud/exp/iat/nbf/역할/용도 검증, 교체 절차 | 샘플 개발 검증 완료, 실제 운영 Secret 배포·교체 인수는 후속 |
| 3.3 | USER/OPERATIONS/ADMIN, 정확한 공개 경로, 호출자/대상별 자격증명+독립 JWT 검증, 기본 loopback, 인증된 Trace sourceService | 샘플 개발 검증 완료, 실제 VM 계정/망/TLS 인수는 후속 |
| 3.4 | opaque Refresh 해시 저장, family 잠금·회전·재사용 폐기, 만료/로그아웃, DB 감사 | 샘플 개발 검증 완료, 운영 보존/복구는 후속 |

권한·키·API·이행·실행 절차는 [보안 기준](../../security/SECURITY_BASELINE.md)을 따른다. 새 업무 DTO 계층은 추가하지 않았으며 기존 Auth 요청/응답과 서비스/mapper/xml/vo 구조를 사용했다. 공통 검증 설정은 여러 서비스에서 명시적 Import로 재사용하며 인증 프로토콜을 직접 구현하지 않았다.

## 검증

- Java 25 전체 build 성공.
- 자동 테스트 20건: Auth/Refresh/JWT 통합 11, Gateway 필터 6, HR HTTP/H2 3. 실패·오류·skip 0.
- HTTP 회귀 97체크 PASS, FAIL/BLOCKED/ERROR 0, 종료 코드 0. 시작 확인 7건과 기능/보안 90체크를 구분한다.
- 실제 UI 중계 API → Gateway → Auth/Member/HR/Config/Trace 경로에서 검증했다. 브라우저 시각/클릭 자동화나 운영 침투 시험을 수행한 것으로 보지 않는다.
- 기존 2단계 40체크는 실제 JWT와 운영 조회용 ADMIN 자격으로 재검증했다. 외부 Trace 쓰기는 이제 route 404 이전에 권한 403으로 거부될 수 있다.
- 최초 보안 HTTP 실행은 94 PASS/1 FAIL이었다. aud 단일 대상이 문자열로 직렬화되는 표준 표현을 시험이 배열만 허용한 문제였으며, 문자열/배열을 정규화해 검사한 최종 실행이 전부 통과했다. alg/kid/서명·잘못된 대상 거부 검증은 유지했다.
- Node 기반 UI 로직 시험으로 메모리 전용 토큰·Refresh 헤더/본문·회전·로그아웃 204·로그인 실패 시 토큰 정리를 확인했다. 실제 브라우저 시험과 구분한다. UI JS 문법, 도구 Python 문법 및 문서 링크도 확인한다. 단일 VM 보안 실행 도구도 최종 대상별 자격증명으로 별도 프로세스의 health UP·직접 무자격 API 401을 확인했다. [실행 도구 증적](evidence/launcher-report.json).

[HTTP 회귀 증적](evidence/security-regression-report.json) · [JUnit 요약](evidence/test-summary.json). 최종 실행 ID: `20261020T005233Z-6122e7`. 로그는 ignored build/verification/stage3에 있다. 키/서비스 토큰/관리자 비밀번호가 있는 security 하위 디렉터리는 문서 증적으로 복사하지 않았다.

### 주요 성공·거부 경로

| 항목 | 결과 |
|---|---|
| 정상 가입/로그인/HR 확인·오류/복구 | 기존 흐름 정상, 종료 상태·Trace 연결 유지 |
| 비밀번호 원문·해시 조회 | 일반 사용자 목록 거부; ADMIN 조회에도 미포함; 저장값 BCrypt 확인 |
| 가입 role=ADMIN 입력 | USER만 생성 |
| 틀린 비밀번호/없는 사용자 | 동일한 401, 토큰 미발급 |
| 약한/UTF-8 72바이트 초과 비밀번호·중복 ID | 400/409 |
| 접두사·alg=none·서명 변조·다른 서명키/알 수 없는 kid | 401 |
| 다른 issuer/aud·만료·미래·누락 claim·잘못된 역할/용도 | 401 |
| 미리 배포한 신규 공개키 | 정상 검증 |
| USER의 운영 조회/ADMIN 쓰기·헤더 권한 위조 | 403 |
| OPERATIONS의 Trace 조회/관리 쓰기 | 조회 허용, 쓰기 거부 |
| 사용자 JWT만으로 서비스 직접 접근 | 401 |
| 서비스 자격만으로 사용자 API 접근·위조 서비스 자격 | 401 |
| Trace writer의 운영 조회·무인증 이벤트 주입 | 403/401 |
| payload sourceService 위조·중복 서비스 ID | 인증 필터의 발행자 ID로 덮어쓰고 중복 인증 헤더 거부 |
| 다른 수신 대상의 서비스 비밀값 재사용 | 401 |
| Refresh 회전/이전 토큰 재사용 | 회전 성공, 재사용 401 및 후속 family 폐기 |
| 동시 Refresh 요청 | 한 건 성공·다른 요청 401; 재사용 정책에 따라 성공 후속도 폐기 |
| 로그아웃 후 Refresh 재사용 | 401 |
| Refresh 만료·재사용 감사 | DB 통합 테스트로 거부와 감사 commit 확인 |

## 재현

```bash
./gradlew --project-cache-dir build/verification/gradle-cache --no-daemon --max-workers=2 build
python tools/verification/stage1_baseline.py --stage3 --java "$JAVA_HOME/bin/java"
```

현재 클라우드에서는 `/workspace/cloud-setup/env.sh`와 Gradle의 `-I /workspace/cloud-setup/proxy.gradle`이 필요하다. 시험은 실행별 임시 키/자격증명·포트·H2를 만들고 자기 프로세스만 종료한다. 현재 수동 실행 중인 이전 프로세스를 재배포했다고 주장하지 않는다.

## 남은 조건과 다음 단계

- 실제 운영 키/자격증명, TLS/mTLS·OS 계정/Secret mount, 키 관리 책임자 및 롤링 교체는 운영 환경에서 검증해야 한다.
- 기존 영속 DB에는 새 auth role/refresh/audit 및 trace source_service migration이 필요하다. H2 샘플은 재기동 시 초기화되며 create-if-not-exists만으로 과거 테이블을 이행하지 않는다. 평문 사용자 데이터는 비밀번호 초기화 절차가 필요하다.
- logout/refresh 폐기는 Refresh family에 적용된다. 이미 발급한 Access JWT는 exp+시계 오차까지 유효할 수 있으며 즉시 Access 폐기를 구현한 것으로 보지 않는다.
- 공개 로그인/가입 Rate Limit, 운영 BFF/CSP, 전체 보안 점검, 감사·만료 행의 보존/정리·백업/HA는 별도 인수 대상이다.
- Outbox·표준 span·DB 장애 정합성·관측/알림·실제 차세대 업무 인수는 WBS 4번 이후다.

WBS 3.1~3.4는 **샘플 개발 검증 완료**다. G1의 응용 인증·권한 기준은 로컬에서 통과했으나 운영 보안 인수까지 완료된 것은 아니다. 전체 운영 도입 가능 판정은 보류하며 다음 구현은 **WBS 4 처리 flow·수집 신뢰성**이다.

재사용 install-security.sh 재실행은 전체 45개 태스크 up-to-date로 성공했고 로컬 v2 fixture 재사용도 확인했다. 생성된 관리자 비밀번호·서비스 토큰·개인키가 버전 관리 대상 파일에 포함되지 않은 것을 값 비교로 검사했다. 이 검사는 전문 비밀정보 탐지나 운영 보안 인수의 대체가 아니다.
