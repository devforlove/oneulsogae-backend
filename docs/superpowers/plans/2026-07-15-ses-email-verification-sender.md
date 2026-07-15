# SES 이메일 인증번호 발송 어댑터 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회사/학교 이메일 인증번호 발송을 로깅 스텁에서 AWS SES 실발송(prod 프로파일 한정)으로 교체한다.

**Architecture:** `@Profile("prod")`에서만 `SesV2Client`+어댑터 2개+공용 발송 컴포넌트가 활성화되고, 기존 로깅 스텁은 `@Profile("!prod")`로 local·test를 계속 담당한다. E2E는 test 프로파일이라 변경 없이 GREEN이어야 한다.

**Tech Stack:** AWS SDK for Java v2 `sesv2`(기존 BOM 2.46.21), Spring `@Profile`, `@ConfigurationPropertiesScan`

**스펙:** `docs/superpowers/specs/2026-07-15-ses-email-verification-sender-design.md`

## Global Constraints

- SES 관련 빈(`SesConfig`·`SesVerificationMailSender`·어댑터 2개)은 전부 `@Profile("prod")`, 로깅 스텁 2개는 `@Profile("!prod")` — 어느 프로파일에서도 포트당 구현 빈이 정확히 1개여야 한다.
- 메일 문구(스펙 명시, 그대로): 제목 `[미플] 회사 이메일 인증번호`/`[미플] 학교 이메일 인증번호`, 본문은 `인증번호: {code}` + 안내 3줄, UTF-8 텍스트.
- 발송 실패는 예외 그대로 전파(래핑 금지).
- 코드 스타일: 탭 들여쓰기, trailing comma, 변수·반환 타입 명시.
- 커밋 1회: `feat(user): 이메일 인증번호 SES 발송 어댑터 추가` + `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` 트레일러 (Task 2에서 회귀 GREEN 확인 후).

---

### Task 1: SES 의존성·설정·어댑터 구현 + 스텁 프로파일 분리

**Files:**
- Modify: `meeple-infra/build.gradle.kts` (sesv2 의존성 추가)
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/config/SesProperties.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/config/SesConfig.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/SesVerificationMailSender.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/SesCompanyEmailVerificationSender.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/SesUniversityEmailVerificationSender.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/LoggingCompanyEmailVerificationSender.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/LoggingUniversityEmailVerificationSender.kt`
- Modify: `meeple-api/src/main/resources/application.yml` (app.ses 블록)

**Interfaces:**
- Consumes: 기존 out-port `SendCompanyEmailVerificationPort`/`SendUniversityEmailVerificationPort`(`send(toEmail: String, code: String)`), AWS SDK BOM
- Produces: prod 프로파일에서 두 포트의 SES 구현 빈. Task 2가 회귀로 검증.

- [ ] **Step 1: build.gradle.kts에 sesv2 의존성 추가**

`meeple-infra/build.gradle.kts`에서 기존 블록:

```kotlin
	implementation(platform("software.amazon.awssdk:bom:2.46.21"))
	implementation("software.amazon.awssdk:s3")
```

바로 아래(`url-connection-client` 선언 위 또는 아래 무관, s3 선언 다음 줄)에 추가:

```kotlin
	// SES(이메일 인증번호 발송) 클라이언트. prod 프로파일에서만 빈이 활성화된다(local·test는 로깅 스텁).
	implementation("software.amazon.awssdk:sesv2")
```

- [ ] **Step 2: SesProperties 작성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/config/SesProperties.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** SES 이메일 발송 설정. (@ConfigurationPropertiesScan으로 자동 등록) */
@ConfigurationProperties(prefix = "app.ses")
data class SesProperties(
	val region: String = "ap-northeast-2",
	/** 발신 주소. SES에서 검증된 도메인의 주소여야 한다. */
	val fromAddress: String = "no-reply@meeple.life",
)
```

- [ ] **Step 3: SesConfig 작성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/config/SesConfig.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client

/**
 * SES 클라이언트 설정. prod 프로파일에서만 활성화된다(local·test는 로깅 스텁이 발송을 대신한다).
 * 자격 증명은 기본 체인(EC2 IAM 인스턴스 롤)을 쓰므로 키 설정이 없다.
 */
@Configuration
@Profile("prod")
class SesConfig(
	private val properties: SesProperties,
) {

	@Bean(destroyMethod = "close")
	fun sesV2Client(): SesV2Client =
		SesV2Client.builder()
			.region(Region.of(properties.region))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.build()
}
```

- [ ] **Step 4: 공용 발송 컴포넌트 작성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/SesVerificationMailSender.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.user.command.adapter

import com.org.meeple.infra.config.SesProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest

/**
 * SES 텍스트 메일 발송 공용 컴포넌트. 회사/학교 인증번호 어댑터가 문구만 달리해 위임한다.
 * 발송 실패(SES 예외)는 그대로 전파한다 — 요청 트랜잭션이 롤백되어 인증 레코드가 남지 않고, 클라이언트가 재시도한다.
 */
@Component
@Profile("prod")
class SesVerificationMailSender(
	private val sesClient: SesV2Client,
	private val properties: SesProperties,
) {

	fun send(toEmail: String, subject: String, body: String) {
		val request: SendEmailRequest = SendEmailRequest.builder()
			.fromEmailAddress(properties.fromAddress)
			.destination(Destination.builder().toAddresses(toEmail).build())
			.content(
				EmailContent.builder()
					.simple(
						Message.builder()
							.subject(Content.builder().data(subject).charset(CHARSET).build())
							.body(Body.builder().text(Content.builder().data(body).charset(CHARSET).build()).build())
							.build(),
					)
					.build(),
			)
			.build()
		sesClient.sendEmail(request)
	}

	companion object {
		private const val CHARSET: String = "UTF-8"
	}
}
```

- [ ] **Step 5: 회사/학교 SES 어댑터 작성**

`SesCompanyEmailVerificationSender.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.SendCompanyEmailVerificationPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** [SendCompanyEmailVerificationPort]의 SES 구현. prod 프로파일에서만 활성화된다. */
@Component
@Profile("prod")
class SesCompanyEmailVerificationSender(
	private val mailSender: SesVerificationMailSender,
) : SendCompanyEmailVerificationPort {

	override fun send(toEmail: String, code: String) {
		mailSender.send(
			toEmail = toEmail,
			subject = "[미플] 회사 이메일 인증번호",
			body = """
				|인증번호: $code
				|
				|미플에서 요청하신 회사 이메일 인증번호입니다.
				|10분 안에 화면에 입력해 주세요.
				|
				|본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
			""".trimMargin(),
		)
	}
}
```

`SesUniversityEmailVerificationSender.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.SendUniversityEmailVerificationPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** [SendUniversityEmailVerificationPort]의 SES 구현. prod 프로파일에서만 활성화된다. */
@Component
@Profile("prod")
class SesUniversityEmailVerificationSender(
	private val mailSender: SesVerificationMailSender,
) : SendUniversityEmailVerificationPort {

	override fun send(toEmail: String, code: String) {
		mailSender.send(
			toEmail = toEmail,
			subject = "[미플] 학교 이메일 인증번호",
			body = """
				|인증번호: $code
				|
				|미플에서 요청하신 학교 이메일 인증번호입니다.
				|10분 안에 화면에 입력해 주세요.
				|
				|본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
			""".trimMargin(),
		)
	}
}
```

- [ ] **Step 6: 로깅 스텁 2개에 @Profile("!prod") 추가**

`LoggingCompanyEmailVerificationSender.kt` — import에 `org.springframework.context.annotation.Profile` 추가, KDoc의 "추후 SMTP/외부 메일 API 어댑터로 교체하면 된다" 문장을 "prod는 SES 어댑터([SesCompanyEmailVerificationSender])가 발송을 담당한다"로 교체, 클래스 선언을:

```kotlin
@Component
@Profile("!prod")
class LoggingCompanyEmailVerificationSender : SendCompanyEmailVerificationPort {
```

`LoggingUniversityEmailVerificationSender.kt`도 동일하게(`@Profile("!prod")` 추가, KDoc은 [SesUniversityEmailVerificationSender] 참조로) 수정.

- [ ] **Step 7: application.yml에 app.ses 블록 추가**

`meeple-api/src/main/resources/application.yml`의 `app.s3` 블록 마지막 줄:

```yaml
    secret-key: ${S3_SECRET_KEY:}
```

바로 아래에 추가 (`kcp:` 블록 위):

```yaml
  # SES(이메일 인증번호 발송). prod 프로파일에서만 SES 어댑터가 활성화된다(local·test는 로깅 스텁).
  ses:
    region: ${SES_REGION:ap-northeast-2}
    from-address: ${SES_FROM_ADDRESS:no-reply@meeple.life}
```

- [ ] **Step 8: 컴파일 확인**

Run: `./gradlew :meeple-infra:compileKotlin :meeple-api:compileKotlin`
Expected: BUILD SUCCESSFUL. **커밋하지 않는다** (Task 2에서 회귀 GREEN 후 커밋).

---

### Task 2: 이메일 인증 E2E 회귀 확인 + 커밋

**Files:**
- 수정 없음 (검증·커밋만)

**Interfaces:**
- Consumes: Task 1의 전체 변경
- Produces: `feat(user)` 커밋

- [ ] **Step 1: 이메일 인증 E2E 4종 회귀 실행**

Run:
```bash
./gradlew :meeple-api:test \
  --tests "com.org.meeple.api.user.RequestCompanyEmailVerificationE2ETest" \
  --tests "com.org.meeple.api.user.ConfirmCompanyEmailVerificationE2ETest" \
  --tests "com.org.meeple.api.user.RequestUniversityEmailVerificationE2ETest" \
  --tests "com.org.meeple.api.user.ConfirmUniversityEmailVerificationE2ETest"
```
Expected: 전 케이스 PASS — test 프로파일은 `@Profile("!prod")` 로깅 스텁 경로를 그대로 쓴다. (여기서 실패하면 프로파일 분리가 잘못된 것 — 특히 SES 빈이 test에서 뜨려고 하면 안 된다)

- [ ] **Step 2: 커밋**

```bash
git add meeple-infra/build.gradle.kts \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/config/SesProperties.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/config/SesConfig.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/ \
        meeple-api/src/main/resources/application.yml
git commit -m "feat(user): 이메일 인증번호 SES 발송 어댑터 추가

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

커밋 후 `git status`로 워킹트리 clean 확인.
