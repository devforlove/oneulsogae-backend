# SES 이메일 인증번호 발송 어댑터 설계

- 날짜: 2026-07-15
- 상태: 승인됨

## 배경

회사/학교 이메일 인증 플로우(`RequestCompanyEmailVerificationService`/`RequestUniversityEmailVerificationService`)는 out-port `SendCompanyEmailVerificationPort`/`SendUniversityEmailVerificationPort.send(toEmail, code)`로 6자리 인증번호(10분 만료)를 발송하는데, 현재 구현은 로그만 남기는 스텁이다. AWS SES 인프라(도메인 `meeple.life` DKIM·MAIL FROM·DMARC 검증, EC2 롤 `ses:SendEmail` 권한)가 준비되어 실발송 어댑터로 교체한다.

## 구성

- **의존성**: `software.amazon.awssdk:sesv2` — 기존 AWS SDK BOM(2.46.21) 아래 추가(meeple-infra).
- **`SesProperties`** (`app.ses`, `@ConfigurationPropertiesScan` 자동 등록): `region`(기본 `ap-northeast-2`, env `SES_REGION`), `fromAddress`(기본 `no-reply@meeple.life`, env `SES_FROM_ADDRESS`). 운영 EC2 `.env` 추가 없이 기본값으로 동작.
- **`SesConfig`** (`@Configuration @Profile("prod")`): `SesV2Client` 빈 — 리전 + `DefaultCredentialsProvider`(IAM 인스턴스 롤) + `UrlConnectionHttpClient`. 로컬 엔드포인트 override 없음(로컬은 스텁 유지 — YAGNI). `destroyMethod = "close"`.

## 어댑터 구조

두 포트의 메서드 시그니처가 동일(`send(toEmail, code)`)해서 한 클래스로 두 포트를 구현하면 문구를 다르게 줄 수 없다. 따라서:

- **`SesVerificationMailSender`** (`@Component @Profile("prod")`): `SesV2Client`로 `(to, subject, body)` 텍스트 메일(UTF-8)을 발송하는 공용 컴포넌트. 요청 조립 중복 방지.
- **`SesCompanyEmailVerificationSender`** / **`SesUniversityEmailVerificationSender`** (`@Component @Profile("prod")`): 각 포트 구현. 문구만 다르고 발송은 공용 컴포넌트에 위임.
- **기존 로깅 스텁 2개**(`LoggingCompanyEmailVerificationSender`/`LoggingUniversityEmailVerificationSender`)에 `@Profile("!prod")` 추가 — local·test는 지금처럼 로그만 남긴다. → **E2E는 test 프로파일이라 SES 빈이 아예 등록되지 않아 실호출이 원천 차단**되고, 기존 이메일 인증 E2E 4종은 변경 없이 GREEN이어야 한다.
- 발송 실패(SES 예외)는 그대로 전파 → 요청 트랜잭션 롤백(인증 레코드 미저장), 클라이언트 재시도. 별도 에러코드 래핑 없음.

## 메일 내용 (텍스트, UTF-8)

- 제목: `[미플] 회사 이메일 인증번호` / `[미플] 학교 이메일 인증번호`
- 본문 (회사, 학교는 "회사"→"학교"만 다름):
  ```
  인증번호: {code}

  미플에서 요청하신 회사 이메일 인증번호입니다.
  10분 안에 화면에 입력해 주세요.

  본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
  ```
  (10분은 도메인 `CODE_TTL`과 일치. HTML 템플릿은 이번 범위 밖)

## 설정 추가 (`meeple-api/src/main/resources/application.yml`)

`app.s3` 블록 뒤에:

```yaml
  # SES(이메일 인증번호 발송). prod 프로파일에서만 SES 어댑터가 활성화된다(local·test는 로깅 스텁).
  ses:
    region: ${SES_REGION:ap-northeast-2}
    from-address: ${SES_FROM_ADDRESS:no-reply@meeple.life}
```

## 테스트·검증

- 어댑터는 요청 조립+SDK 호출뿐인 얇은 계층이라 전용 테스트 없음(레포 전략: 도메인→유닛, api→E2E).
- 회귀: 기존 이메일 인증 E2E 4종(`Request/ConfirmCompanyEmailVerificationE2ETest`, `Request/ConfirmUniversityEmailVerificationE2ETest`)이 변경 없이 GREEN.
- 실발송 검증은 배포 후 운영에서 인증 요청 1회로 확인. **샌드박스 해제(프로덕션 액세스) 승인 전에는 검증된 수신자로만 성공**하므로 승인 후 배포가 안전하다.

## 커밋

`feat(user): 이메일 인증번호 SES 발송 어댑터 추가` 1회.
