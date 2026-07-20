# KCP 본인확인(실명·성인·중복가입 차단) 설계

- 작성일: 2026-07-09
- 도메인: `user` (command side)
- 목적: NHN KCP 본인확인 API를 연동해 **실명/휴대폰 실인증 + 성인(만 나이) 인증 + 중복가입 차단(CI/DI)** 을 온보딩 필수 단계로 추가한다.

## 1. 배경 / 현재 상태

- `UserDetail`에 `phoneNumber`·`birthday`·`gender`는 있으나 **실명·CI/DI 등 본인확인 데이터는 없다.** 기존 "인증"은 회사/학교 이메일·재직 이미지 3종뿐이다.
- 프로젝트에 **외부 HTTP 클라이언트(RestClient/WebClient 등)가 아직 없다.** KCP 서버-투-서버 호출용으로 신규 도입이 필요하다.
- KCP 본인확인 흐름: **거래등록(서버) → 인증창 호출(프론트 JS) → 결과조회·복호화(서버)**. 인증창 호출은 프론트 영역이므로 이 저장소(백엔드)에서는 프론트 대응 사항을 안내만 한다.

## 2. 결정 사항 (확정)

| 항목 | 결정 |
|---|---|
| 목적 | 실명/휴대폰 실인증 + 성인 인증(만 나이) + 중복가입 차단(CI/DI) 전부 |
| 플랫폼 | 웹·모바일 둘 다 (백엔드 register/confirm API 공통, 리다이렉트만 프론트 분기) |
| 시점 | 온보딩 **첫 관문** (OAuth 직후·정보입력 전) |
| 자격증명 | KCP **테스트키로 먼저** 구현, 운영 실키는 환경변수로 교체 |
| 암호화 | KCP `encrypJson`/`decryptJson`을 **포트로 격리**. 지금은 stub(TODO), JAR 확보 시 구현체만 교체 |
| 도메인 위치 | 신규 도메인 없이 **user 도메인 command side**에 추가 (기존 이메일 인증과 동일 패턴) |
| CI/DI 저장 | **DI 평문**(중복조회용) + **CI 앱단 암호화**(AES-GCM, 환경변수 키). 응답/로그 노출 금지 |

## 3. 아키텍처

기존 컨벤션(회사/학교 이메일 인증이 user 도메인 command side에 위치)을 따른다.

```
oneulsogae-core (core/user/command)
  domain/IdentityVerification.kt
    - 애그리거트: userId, ordrIdxx(주문번호), regCertKey, status, 검증결과(이름/생년월일/성별/전화/CI/DI/통신사/내외국인), verifiedAt
    - 도메인 행위: create(요청생성), validateRegCertKey(위변조검증), validateAdult(now)(만 나이), complete(결과반영)
  domain/policy/IdentityDuplicationPolicy.kt (또는 도메인 메서드) - DI 중복 판정 캡슐화
  application/RegisterIdentityVerificationService  - 거래등록 시작
  application/ConfirmIdentityVerificationService   - 결과조회+복호화+검증+저장+중복차단+상태전이
  port/in
    RegisterIdentityVerificationUseCase
    ConfirmIdentityVerificationUseCase (+command/ConfirmIdentityVerificationCommand, result/…Result)
  port/out
    KcpCertRegisterPort   - 거래등록 HTTP
    KcpCertQueryPort      - 결과조회 HTTP
    KcpCertCryptoPort     - encrypt/decrypt (encrypJson/decryptJson 추상화)
    SaveIdentityVerificationPort, GetIdentityVerificationPort
    ExistsIdentityByDiPort - 중복가입 차단 조회
    (기존 재사용) SaveUserDetailPort, GetUserPort/SaveUserPort(상태전이), TimeGenerator

oneulsogae-infra (infra/user/command)
  entity/IdentityVerificationEntity + repository + mapper + adapter(Save/Get/ExistsByDi 구현)
  adapter/KcpCertClientAdapter   - RestClient로 testcert/cert.kcp.co.kr 호출 (Register·Query 포트 구현)
  adapter/KcpCertCryptoStubAdapter - KcpCertCryptoPort 구현. encrypt/decrypt 자리표시(TODO). JAR 확보 시 이 클래스만 교체
  config/KcpProperties (@ConfigurationProperties "app.kcp.*") + RestClient 빈
  crypto/CiCipher - CI 앱단 AES-GCM 암복호화 유틸(환경변수 키). 어댑터가 저장 전 CI 암호화

oneulsogae-api (api/user)
  IdentityVerificationController  (/user/v1/identity-verification)
    POST /register  → { callUrl, regCertKey, ordrIdxx }
    POST /confirm   → 검증 요약 반환
  request/response DTO
```

**핵심**: 암호화만 stub, 나머지(거래등록·결과조회 HTTP·도메인·API·중복차단·저장·상태전이)는 전부 구현한다. KCP JAR 확보 시 `KcpCertCryptoStubAdapter`만 교체하면 실연동된다.

## 4. 데이터 흐름 (웹·모바일 공통)

```
1. [프론트] 온보딩 진입 → POST /user/v1/identity-verification/register (@LoginUser)
2. [백엔드] ordr_idxx 생성 → KcpCertCryptoPort.encrypt(거래등록 params) → enc_data, rv
           → KcpCertRegisterPort: POST https://testcert.kcp.co.kr/api/reg/certDataReg.do
           → res_cd=0000이면 reg_cert_key·call_url 수신 → IdentityVerification(REQUESTED) 저장
           → 응답: { callUrl, regCertKey, ordrIdxx }   (Ret_URL은 거래등록 params에 프론트 콜백 URL로 포함)
3. [프론트] call_url + reg_cert_key로 KCP 인증창 오픈 (웹=팝업 / 모바일=페이지전환·웹뷰)
           → 통신사 본인인증 → KCP가 Ret_URL로 결과 리턴
4. [프론트] Ret_URL 페이지에서 결과 수신 → POST /confirm { regCertKey, ordrIdxx }
5. [백엔드] 저장 레코드와 regCertKey·ordrIdxx 일치 검증(위변조 방지)
           → KcpCertQueryPort: POST https://testcert.kcp.co.kr/api/query/getCertData.do → enc_cert_data 수신
           → KcpCertCryptoPort.decrypt → 이름·생년월일·성별·전화·CI·DI 등 평문
           → 도메인 검증: validateAdult(now)(만 나이) · DI 중복 차단
           → IdentityVerification(VERIFIED) + UserDetail(생년월일·성별·전화 반영) + User 상태 전이
           → 응답: { name, isAdult, verified: true } (CI/DI 미포함)
```

- KCP 엔드포인트(테스트/운영):
  - 거래등록: `https://testcert.kcp.co.kr/api/reg/certDataReg.do` / `https://cert.kcp.co.kr/api/reg/certDataReg.do`
  - 결과조회: `https://testcert.kcp.co.kr/api/query/getCertData.do` / `https://cert.kcp.co.kr/api/query/getCertData.do`
- Ret_URL(인증창 결과 리턴 주소)은 **프론트 페이지**다. 백엔드에 공개 콜백 엔드포인트를 두지 않으므로 SecurityConfig permitAll이 불필요하고 register/confirm 둘 다 `@LoginUser` 인증이 필요하다.

## 5. 상태 흐름 (첫 관문)

`UserStatus`에 `IDENTITY_VERIFICATION_PENDING`를 추가하고 신규 가입 초기 상태로 삼는다.

```
가입 직후: IDENTITY_VERIFICATION_PENDING   ← User.create 초기 상태 변경
  → 본인확인 통과(passIdentityVerification) → ONBOARDING (추가정보 입력)
  → 정보입력 → EMAIL_VERIFICATION_PENDING → (COMPANY_NOT_RESOLVED) → ACTIVE
```

- `User.create`의 초기 상태를 `IDENTITY_VERIFICATION_PENDING`로 변경, `passIdentityVerification(): User`(→ ONBOARDING) 전이 메서드 추가.
- 온보딩 정보입력 시 생년월일·성별·전화번호는 **검증값을 프리필/고정**한다(재입력 방지). 온보딩 입력 DTO/서비스에서 해당 3개 필드는 검증값을 신뢰값으로 사용.
- `isMatchable()`/`isRegistered()` 등 기존 판정에는 신규 상태를 포함하지 않는다(정식가입·매칭 대상 아님).

## 6. 데이터 모델

신규 테이블 `identity_verification`:

| 컬럼 | 설명 |
|---|---|
| id (PK) | |
| user_id | 소유자 (FK 개념) |
| ordr_idxx | 주문번호 (KCP 거래 추적) |
| reg_cert_key | 거래등록키 (위변조 검증) |
| status | REQUESTED / VERIFIED / FAILED |
| real_name | 검증된 실명 |
| birthday | 검증 생년월일 (YYYYMMDD → LocalDate) |
| gender | 검증 성별 (공용 Gender enum 매핑) |
| phone_number | 검증 전화번호 |
| di | **평문** 저장 — 중복조회용. 인덱스(+유니크 검토) |
| ci_encrypted | **앱단 AES-GCM 암호문** 저장 |
| foreigner | 내/외국인 구분 |
| telecom | 통신사 코드 |
| verified_at | |
| created_at | |

- **CI/DI 노출 금지**: 응답 DTO·로그에 절대 포함하지 않는다.
- **중복 조회**: DI 기준. `di` 컬럼 인덱스로 seek. 다른 `WITHDRAWN`이 아닌 유저에 동일 DI가 존재하면 중복으로 판정.
- **UserDetail 반영**: 검증된 `birthday·gender·phoneNumber`를 신뢰값으로 반영. `real_name`(실명)은 `IdentityVerification`에만 보관(표시용 이름은 기존 `nickname` 유지) → UserDetail 스키마 변경 최소화.

## 7. 에러 처리

`UserErrorCode`에 추가:

| 코드 | 상황 |
|---|---|
| `KCP_REGISTER_FAILED` | 거래등록 res_cd ≠ 0000 |
| `KCP_QUERY_FAILED` | 결과조회 res_cd ≠ 0000 |
| `IDENTITY_VERIFICATION_MISMATCH` | confirm 시 regCertKey·ordrIdxx 불일치(위변조) |
| `IDENTITY_NOT_ADULT` | 만 나이 미성년 |
| `IDENTITY_ALREADY_REGISTERED` | DI 중복(이미 가입된 사용자) |

- KCP 통신 실패(네트워크·비정상 응답)는 어댑터에서 잡아 위 도메인 에러로 변환한다.
- `if…throw` 나열 대신 `IdentityVerification`의 `validate…`/정책 함수로 캡슐화한다(성인 판정·regCertKey 일치·중복 판정).

## 8. 테스트

- **도메인 유닛(Kotest)**: `IdentityVerification`
  - `validateAdult` 경계(만 19세 생일 전날/당일/다음날, `now`·`birthday` 파라미터 주입)
  - 상태 전이(REQUESTED→VERIFIED/FAILED)
  - `validateRegCertKey` 불일치 예외
  - DI 중복 정책
- **E2E(oneulsogae-api)**: register→confirm 플로우
  - KCP out-port(Register/Query/Crypto)는 실호출 불가 → **테스트 컨텍스트에서 페이크 빈으로 대체**(고정 복호화 결과 주입)
  - 케이스: 성공(성인·비중복) / 미성년 / DI 중복 / regCertKey 불일치 / 거래등록 실패
  - `AbstractIntegrationSupport` + `IntegrationUtil`/픽스처 + `RestAssuredDsl`

## 9. 설정 (환경변수)

`application.yml`에 `app.kcp.*` 추가, `@ConfigurationProperties`로 바인딩:

```yaml
app:
  kcp:
    site-cd: ${KCP_SITE_CD:}         # 테스트 사이트코드
    enc-key: ${KCP_ENC_KEY:}         # 테스트 암호화키
    web-siteid: ${KCP_WEB_SITEID:}
    base-url: ${KCP_BASE_URL:https://testcert.kcp.co.kr}
    ret-url: ${KCP_RET_URL:}         # 프론트 결과 리턴 페이지
  identity:
    ci-encryption-key: ${IDENTITY_CI_KEY:}   # CI 앱단 AES-GCM 키
```

운영 전환은 `base-url`·`site-cd`·`enc-key`를 운영 값으로 교체하고 `KcpCertCryptoStubAdapter`를 실 JAR 구현으로 대체한다.

## 10. 프론트엔드 대응 (직접 수정하지 않음 — 안내)

> 저장소 경계 규칙상 `meeple-frontend`는 수정하지 않는다. 아래는 백엔드 변경에 맞춰 프론트가 대응해야 할 내용이다.

- **KCP 인증창 오픈**: `/register` 응답의 `callUrl` + `regCertKey`로 폼 전송(웹=팝업, 모바일=페이지전환/웹뷰).
- **Ret_URL 결과 수신 페이지 신설**: KCP가 리턴한 결과에서 `regCertKey`·`ordrIdxx`를 추출해 백엔드 `POST /user/v1/identity-verification/confirm` 호출.
- **온보딩 화면**: 본인확인(첫 관문) 완료 전 정보입력 차단, 생년월일·성별·전화번호는 검증값 프리필·읽기전용.
- **응답 DTO 대응**: register `{ callUrl, regCertKey, ordrIdxx }`, confirm `{ name, isAdult, verified }`.

## 11. 미해결/후속

- KCP 공식 Java 라이브러리 JAR 확보 → `KcpCertCryptoStubAdapter` 실구현 교체(거래등록 enc_data 생성 · 결과 복호화 알고리즘 = KCP 매뉴얼 기준).
- 거래등록 요청 파라미터 전체 목록은 KCP API Reference(`/reference/regist`) 확정본으로 구현 시 재확인.
- `di` 유니크 제약을 DB에 걸지(하드 차단) 애플리케이션 레벨에서만 검사할지 실DB DDL 반영 시점에 결정.
