# KCP 본인확인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** NHN KCP 본인확인 API를 user 도메인 command side에 연동해, 온보딩 첫 관문에서 실명/휴대폰 실인증 + 성인 인증 + CI/DI 중복가입 차단을 수행한다.

**Architecture:** 헥사고날. 도메인(`IdentityVerification`)·유스케이스(`Register`/`Confirm`)·포트(KCP register/query/crypto + 영속성)를 `oneulsogae-core`에 두고, `oneulsogae-infra`가 HTTP 어댑터(RestClient)·영속성 어댑터·암호화 stub을 구현한다. `oneulsogae-api`에 컨트롤러를 둔다. KCP 암호화(`encrypJson`/`decryptJson`)는 `KcpCertCryptoPort`로 격리해 지금은 passthrough stub, 공식 JAR 확보 시 구현체만 교체한다.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 4.0.6, Spring Data JPA, Spring `RestClient`, JDK `javax.crypto`(AES-GCM), Kotest(도메인 유닛), Testcontainers + RestAssured(E2E).

## Global Constraints

- 응답/네이밍/커밋 등 규칙은 `CLAUDE.md`를 따른다. 응답은 한국어. `meeple-backend`만 수정하고 프론트는 안내만 한다.
- **패키지 루트**: `com.org.oneulsogae`. in-port 패키지는 백틱 `` `in` `` 사용.
- **시각**: `LocalDateTime.now()` 직접 호출 금지. `com.org.oneulsogae.core.common.time.TimeGenerator` 주입, 도메인엔 `now`/`today` 파라미터로 전달.
- **타입 명시**: 변수·반환·람다 파라미터 타입 생략 금지.
- **엔티티**: `com.org.oneulsogae.infra.common.BaseEntity` 상속(`id: Long?`, `created_at`/`updated_at`/`deleted_at` 제공), `@SQLRestriction("deleted_at is null")`.
- **에러**: `UserErrorCode`(enum, `ErrorCode` 구현) + `BusinessException(errorCode[, message])`. 신규 코드는 `USER-024`부터.
- **CI/DI 노출 금지**: 응답 DTO·로그에 CI/DI 포함 금지. DI는 평문 저장(중복조회), CI는 앱단 AES-GCM 암호화 저장.
- **CQRS**: 본인확인은 command side. 조회 read model 불필요(확정 응답만 반환).
- **컨트롤러 경로 규칙**: 유저 엔드포인트는 `/users/v1/...`(복수형). 인증 필요(SecurityConfig의 `.anyRequest().authenticated()`가 자동 적용 — permitAll 추가 불필요).

---

### Task 1: UserStatus 신규 상태 + User 전이

가입 초기 상태를 `IDENTITY_VERIFICATION_PENDING`로 바꾸고, 본인확인 통과 시 `ONBOARDING`으로 전이한다.

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/user/UserStatus.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/User.kt`
- Modify(test): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/auth/OAuthLoginIntegrationTest.kt:48`
- Modify(test): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/PurgeWithdrawnUserE2ETest.kt:66`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserIdentityVerificationTest.kt` (create)

**Interfaces:**
- Produces: `UserStatus.IDENTITY_VERIFICATION_PENDING`; `User.create(...)`가 이 상태로 생성; `User.passIdentityVerification(): User`(→ ONBOARDING).

- [ ] **Step 1: 실패 테스트 작성** — `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserIdentityVerificationTest.kt`

```kotlin
package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.domain.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserIdentityVerificationTest : DescribeSpec({

	describe("create") {
		it("신규 가입 사용자는 본인확인 대기(IDENTITY_VERIFICATION_PENDING) 상태로 생성된다") {
			val user: User = User.create(provider = "kakao", providerId = "pid-1", email = "u@test.com")
			user.status shouldBe UserStatus.IDENTITY_VERIFICATION_PENDING
		}
	}

	describe("passIdentityVerification") {
		it("본인확인을 통과하면 온보딩(ONBOARDING) 상태로 전이한다") {
			val user: User = User.create(provider = "kakao", providerId = "pid-1", email = "u@test.com")
			user.passIdentityVerification().status shouldBe UserStatus.ONBOARDING
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.UserIdentityVerificationTest"`
Expected: FAIL — `IDENTITY_VERIFICATION_PENDING` / `passIdentityVerification` 미정의로 컴파일 실패.

- [ ] **Step 3: UserStatus에 상태 추가** — `UserStatus.kt`의 `ONBOARDING` 위에 추가

```kotlin
	/** OAuth 인증 후 본인확인(KCP)만 남은 상태. 본인확인 통과 전까지 온보딩 정보 입력 불가. */
	IDENTITY_VERIFICATION_PENDING,

	/** OAuth 인증만 완료한 상태. 아직 정식 가입(추가 정보 입력) 전. */
	ONBOARDING,
```

`isRegistered()`/`isMatchable()`는 변경하지 않는다(신규 상태는 정식가입·매칭 대상 아님).

- [ ] **Step 4: User.create / passIdentityVerification 구현** — `User.kt`

`companion object`의 `create`를 상태 명시로 변경:

```kotlin
		/** OAuth 인증 직후의 신규 사용자를 생성한다. (status IDENTITY_VERIFICATION_PENDING) */
		fun create(provider: String, providerId: String, email: String?): User =
			User(
				provider = provider,
				providerId = providerId,
				email = email,
				status = UserStatus.IDENTITY_VERIFICATION_PENDING,
			)
```

전이 메서드 추가(`startEmailVerification` 위):

```kotlin
	/** 본인확인(KCP)을 통과하고 온보딩 정보 입력 단계로 진입한다. (IDENTITY_VERIFICATION_PENDING -> ONBOARDING) */
	fun passIdentityVerification(): User =
		copy(status = UserStatus.ONBOARDING)
```

data class의 기본값 `status: UserStatus = UserStatus.ONBOARDING`은 건드리지 않는다(매퍼가 항상 상태를 명시 전달).

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.UserIdentityVerificationTest"`
Expected: PASS

- [ ] **Step 6: 기존 상태 단언 갱신**

`OAuthLoginIntegrationTest.kt:48` — `user.status shouldBe UserStatus.ONBOARDING` → `UserStatus.IDENTITY_VERIFICATION_PENDING`.
`PurgeWithdrawnUserE2ETest.kt:66` — `newUser.status shouldBe UserStatus.ONBOARDING` → `UserStatus.IDENTITY_VERIFICATION_PENDING` (주석 `// 복구가 아닌 신규 → 온보딩부터` → `// 복구가 아닌 신규 → 본인확인부터`).

- [ ] **Step 7: 회귀 확인 + 커밋**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.auth.OAuthLoginIntegrationTest" --tests "com.org.oneulsogae.api.user.PurgeWithdrawnUserE2ETest"`
Expected: PASS

```bash
git add oneulsogae-common oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/User.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserIdentityVerificationTest.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/auth/OAuthLoginIntegrationTest.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/PurgeWithdrawnUserE2ETest.kt
git commit -m "feat(user): 본인확인 대기 상태(IDENTITY_VERIFICATION_PENDING)와 통과 전이 추가"
```

---

### Task 2: 에러코드 + 도메인 모델(IdentityVerification, CertifiedIdentity)

본인확인 애그리거트와 복호화 결과 값 객체, 성인/위변조 검증을 도메인에 캡슐화한다.

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/IdentityVerificationStatus.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/CertifiedIdentity.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/IdentityVerification.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/IdentityVerificationTest.kt` (create)

**Interfaces:**
- Consumes: `UserErrorCode`(Task 1과 무관), `com.org.oneulsogae.common.user.Gender`.
- Produces:
  - `IdentityVerificationStatus { REQUESTED, VERIFIED, FAILED }`
  - `CertifiedIdentity(realName, birthday: LocalDate, gender: Gender, phoneNumber, ci, di, foreigner: Boolean, telecom)` with `age(today): Int`, `isAdult(today): Boolean`
  - `IdentityVerification(id=0, userId, ordrIdxx, regCertKey, status, realName?, birthday?, gender?, phoneNumber?, ci?, di?, foreigner?, telecom?, verifiedAt?)` with `validateForConfirm(regCertKey, ordrIdxx)`, `complete(certified, today, at): IdentityVerification`, companion `request(userId, ordrIdxx, regCertKey)`
  - 신규 `UserErrorCode`: `KCP_REGISTER_FAILED`, `KCP_QUERY_FAILED`, `IDENTITY_VERIFICATION_NOT_FOUND`, `IDENTITY_VERIFICATION_MISMATCH`, `IDENTITY_ALREADY_VERIFIED`, `IDENTITY_NOT_ADULT`, `IDENTITY_ALREADY_REGISTERED`

- [ ] **Step 1: 에러코드 추가** — `UserErrorCode.kt`의 마지막 상수(`INVALID_COMPANY_NAME`) 뒤, `;` 앞이 아니라 enum 상수 목록 끝(마지막 `,` 다음)에 추가

```kotlin
	// 본인확인(KCP identity_verification)
	KCP_REGISTER_FAILED("USER-024", "본인확인 거래등록에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY),
	KCP_QUERY_FAILED("USER-025", "본인확인 결과 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY),
	IDENTITY_VERIFICATION_NOT_FOUND("USER-026", "본인확인 요청 내역이 없습니다. 처음부터 다시 시도해 주세요.", HttpStatus.NOT_FOUND),
	IDENTITY_VERIFICATION_MISMATCH("USER-027", "본인확인 거래 정보가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
	IDENTITY_ALREADY_VERIFIED("USER-028", "이미 완료된 본인확인 요청입니다.", HttpStatus.CONFLICT),
	IDENTITY_NOT_ADULT("USER-029", "만 19세 이상만 가입할 수 있습니다.", HttpStatus.BAD_REQUEST),
	IDENTITY_ALREADY_REGISTERED("USER-030", "이미 본인확인으로 가입된 사용자입니다.", HttpStatus.CONFLICT),
```

- [ ] **Step 2: 실패 테스트 작성** — `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/IdentityVerificationTest.kt`

```kotlin
package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class IdentityVerificationTest : DescribeSpec({

	val today: LocalDate = LocalDate.of(2026, 7, 9)
	val at: LocalDateTime = LocalDateTime.of(2026, 7, 9, 12, 0)

	fun certified(birthday: LocalDate): CertifiedIdentity =
		CertifiedIdentity(
			realName = "홍길동", birthday = birthday, gender = Gender.MALE,
			phoneNumber = "01012345678", ci = "CI-VALUE", di = "DI-VALUE",
			foreigner = false, telecom = "SKT",
		)

	fun requested(): IdentityVerification =
		IdentityVerification.request(userId = 1L, ordrIdxx = "ORD-1", regCertKey = "REG-1")

	describe("CertifiedIdentity.isAdult") {
		it("만 19세 생일 당일이면 성인이다") {
			certified(LocalDate.of(2007, 7, 9)).isAdult(today) shouldBe true
		}
		it("만 19세 생일 하루 전이면 미성년이다") {
			certified(LocalDate.of(2007, 7, 10)).isAdult(today) shouldBe false
		}
	}

	describe("request") {
		it("REQUESTED 상태로 생성된다") {
			requested().status shouldBe IdentityVerificationStatus.REQUESTED
		}
	}

	describe("validateForConfirm") {
		it("regCertKey/ordrIdxx가 다르면 MISMATCH를 던진다") {
			val exception = shouldThrow<BusinessException> {
				requested().validateForConfirm(regCertKey = "REG-1", ordrIdxx = "OTHER")
			}
			exception.errorCode shouldBe UserErrorCode.IDENTITY_VERIFICATION_MISMATCH
		}
		it("이미 VERIFIED면 ALREADY_VERIFIED를 던진다") {
			val verified: IdentityVerification = requested().complete(certified(LocalDate.of(1996, 1, 1)), today, at)
			val exception = shouldThrow<BusinessException> {
				verified.validateForConfirm(regCertKey = "REG-1", ordrIdxx = "ORD-1")
			}
			exception.errorCode shouldBe UserErrorCode.IDENTITY_ALREADY_VERIFIED
		}
	}

	describe("complete") {
		it("성인이면 VERIFIED로 전이하고 검증값을 채운다") {
			val verified: IdentityVerification = requested().complete(certified(LocalDate.of(1996, 1, 1)), today, at)
			verified.status shouldBe IdentityVerificationStatus.VERIFIED
			verified.realName shouldBe "홍길동"
			verified.di shouldBe "DI-VALUE"
			verified.verifiedAt shouldBe at
		}
		it("미성년이면 IDENTITY_NOT_ADULT를 던진다") {
			val exception = shouldThrow<BusinessException> {
				requested().complete(certified(LocalDate.of(2010, 1, 1)), today, at)
			}
			exception.errorCode shouldBe UserErrorCode.IDENTITY_NOT_ADULT
		}
	}
})
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.IdentityVerificationTest"`
Expected: FAIL — 도메인 클래스 미정의로 컴파일 실패.

- [ ] **Step 4: IdentityVerificationStatus 작성**

```kotlin
package com.org.oneulsogae.core.user.command.domain

/** 본인확인 거래 상태. */
enum class IdentityVerificationStatus {
	/** 거래등록만 완료(인증창 호출 전/후, 결과 확정 전). */
	REQUESTED,

	/** 결과 확정·검증 완료. */
	VERIFIED,

	/** 결과 확정 실패(성인 아님 등). */
	FAILED,
}
```

- [ ] **Step 5: CertifiedIdentity 작성**

```kotlin
package com.org.oneulsogae.core.user.command.domain

import com.org.oneulsogae.common.user.Gender
import java.time.LocalDate
import java.time.Period

/**
 * KCP 결과 조회·복호화로 얻은 검증된 신원 정보(값 객체).
 * ci/di는 웹 노출 금지 대상이며, 서비스는 도메인 판정 결과만 사용한다.
 */
data class CertifiedIdentity(
	val realName: String,
	val birthday: LocalDate,
	val gender: Gender,
	val phoneNumber: String,
	val ci: String,
	val di: String,
	val foreigner: Boolean,
	val telecom: String,
) {
	companion object {
		const val ADULT_AGE: Int = 19
	}

	fun age(today: LocalDate): Int = Period.between(birthday, today).years

	fun isAdult(today: LocalDate): Boolean = age(today) >= ADULT_AGE
}
```

- [ ] **Step 6: IdentityVerification 작성**

```kotlin
package com.org.oneulsogae.core.user.command.domain

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 본인확인(KCP) 애그리거트. 거래등록 시 REQUESTED로 생성되고, 결과 확정 시 VERIFIED로 전이하며 검증값을 담는다.
 * 영속성은 [com.org.oneulsogae.infra.user.command.entity.IdentityVerificationEntity]가 담당한다.
 */
data class IdentityVerification(
	val id: Long = 0,
	val userId: Long,
	val ordrIdxx: String,
	val regCertKey: String,
	val status: IdentityVerificationStatus,
	val realName: String? = null,
	val birthday: LocalDate? = null,
	val gender: Gender? = null,
	val phoneNumber: String? = null,
	val ci: String? = null,
	val di: String? = null,
	val foreigner: Boolean? = null,
	val telecom: String? = null,
	val verifiedAt: LocalDateTime? = null,
) {

	/** confirm 요청의 거래 정보가 저장된 거래와 일치하는지(위변조) + 미확정 상태인지 검증한다. */
	fun validateForConfirm(regCertKey: String, ordrIdxx: String) {
		if (this.regCertKey != regCertKey || this.ordrIdxx != ordrIdxx) {
			throw BusinessException(UserErrorCode.IDENTITY_VERIFICATION_MISMATCH)
		}
		if (status == IdentityVerificationStatus.VERIFIED) {
			throw BusinessException(UserErrorCode.IDENTITY_ALREADY_VERIFIED)
		}
	}

	/** 검증된 신원으로 확정한다. 성인이 아니면 예외를 던진다. */
	fun complete(certified: CertifiedIdentity, today: LocalDate, at: LocalDateTime): IdentityVerification {
		if (!certified.isAdult(today)) {
			throw BusinessException(UserErrorCode.IDENTITY_NOT_ADULT)
		}
		return copy(
			status = IdentityVerificationStatus.VERIFIED,
			realName = certified.realName,
			birthday = certified.birthday,
			gender = certified.gender,
			phoneNumber = certified.phoneNumber,
			ci = certified.ci,
			di = certified.di,
			foreigner = certified.foreigner,
			telecom = certified.telecom,
			verifiedAt = at,
		)
	}

	companion object {

		/** 거래등록 직후의 본인확인 요청을 생성한다. (status REQUESTED) */
		fun request(userId: Long, ordrIdxx: String, regCertKey: String): IdentityVerification =
			IdentityVerification(
				userId = userId,
				ordrIdxx = ordrIdxx,
				regCertKey = regCertKey,
				status = IdentityVerificationStatus.REQUESTED,
			)
	}
}
```

- [ ] **Step 7: 테스트 통과 확인 + 커밋**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.IdentityVerificationTest"`
Expected: PASS

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/IdentityVerificationStatus.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/CertifiedIdentity.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/IdentityVerification.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/IdentityVerificationTest.kt
git commit -m "feat(user): 본인확인 도메인 모델·검증값 객체·에러코드 추가"
```

---

### Task 3: 포트 + 유스케이스 + command/result

core의 in-port/out-port와 command/result 타입을 정의한다(인터페이스만 — 컴파일로 검증).

**Files:** (모두 create)
- out: `.../port/out/KcpCertRegisterPort.kt`, `CertRegisterCommand.kt`, `CertRegisterResult.kt`
- out: `.../port/out/KcpCertQueryPort.kt`
- out: `.../port/out/KcpCertCryptoPort.kt`
- out: `.../port/out/SaveIdentityVerificationPort.kt`, `GetIdentityVerificationPort.kt`, `ExistsIdentityByDiPort.kt`
- in: `.../port/in/RegisterIdentityVerificationUseCase.kt`, `ConfirmIdentityVerificationUseCase.kt`
- in: `.../port/in/command/ConfirmIdentityVerificationCommand.kt`
- in: `.../port/in/result/RegisterIdentityVerificationResult.kt`, `ConfirmIdentityVerificationResult.kt`

(경로 접두: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application`)

**Interfaces:**
- Consumes: `IdentityVerification`, `CertifiedIdentity`(Task 2).
- Produces: 아래 시그니처 전부. 이후 Task 4·5(서비스), 6·7(어댑터), 8(컨트롤러)가 소비.

- [ ] **Step 1: out-port — KCP register**

`port/out/CertRegisterCommand.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

/** KCP 거래등록 입력. Ret_URL·site_cd 등 KCP 고정 파라미터는 어댑터가 설정에서 채운다. */
data class CertRegisterCommand(
	val ordrIdxx: String,
)
```
`port/out/CertRegisterResult.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

/** KCP 거래등록 결과. 프론트가 인증창 호출에 사용한다. */
data class CertRegisterResult(
	val regCertKey: String,
	val callUrl: String,
)
```
`port/out/KcpCertRegisterPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

/** KCP 본인확인 거래등록 아웃포트. (testcert/cert.kcp.co.kr/api/reg/certDataReg.do) */
fun interface KcpCertRegisterPort {
	fun register(command: CertRegisterCommand): CertRegisterResult
}
```

- [ ] **Step 2: out-port — KCP query**

`port/out/KcpCertQueryPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity

/**
 * KCP 본인확인 결과조회 아웃포트. 결과조회(getCertData.do) + 복호화 + KCP 필드 매핑까지 어댑터가 수행해
 * 검증된 신원([CertifiedIdentity])만 반환한다. (KCP JSON 세부는 infra에 은닉)
 */
fun interface KcpCertQueryPort {
	fun query(regCertKey: String, ordrIdxx: String): CertifiedIdentity
}
```

- [ ] **Step 3: out-port — KCP crypto**

`port/out/KcpCertCryptoPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

/**
 * KCP 암호화 아웃포트. 거래등록 enc_data 생성(encrypJson)과 결과 복호화(decryptJson)를 격리한다.
 * 현재는 stub(passthrough). KCP 공식 라이브러리 확보 시 구현체만 교체한다.
 */
interface KcpCertCryptoPort {
	fun encryptRegisterData(plainJson: String): String

	fun decryptCertData(encCertData: String): String
}
```

- [ ] **Step 4: out-port — 영속성**

`port/out/SaveIdentityVerificationPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.IdentityVerification

interface SaveIdentityVerificationPort {
	fun save(verification: IdentityVerification): IdentityVerification
}
```
`port/out/GetIdentityVerificationPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.IdentityVerification

interface GetIdentityVerificationPort {
	fun findLatestByUserId(userId: Long): IdentityVerification?
}
```
`port/out/ExistsIdentityByDiPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

/** 중복가입 차단: 다른 사용자가 이미 같은 DI로 본인확인(VERIFIED)했는지. */
interface ExistsIdentityByDiPort {
	fun existsVerifiedByDiOnOtherUser(di: String, userId: Long): Boolean
}
```

- [ ] **Step 5: in-port command/result**

`port/in/command/ConfirmIdentityVerificationCommand.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`.command

data class ConfirmIdentityVerificationCommand(
	val regCertKey: String,
	val ordrIdxx: String,
)
```
`port/in/result/RegisterIdentityVerificationResult.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`.result

data class RegisterIdentityVerificationResult(
	val callUrl: String,
	val regCertKey: String,
	val ordrIdxx: String,
)
```
`port/in/result/ConfirmIdentityVerificationResult.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`.result

/** CI/DI 등 민감정보는 절대 포함하지 않는다. */
data class ConfirmIdentityVerificationResult(
	val name: String,
	val adult: Boolean,
)
```

- [ ] **Step 6: in-port UseCase**

`port/in/RegisterIdentityVerificationUseCase.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.result.RegisterIdentityVerificationResult

interface RegisterIdentityVerificationUseCase {
	fun register(userId: Long): RegisterIdentityVerificationResult
}
```
`port/in/ConfirmIdentityVerificationUseCase.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.command.ConfirmIdentityVerificationCommand
import com.org.oneulsogae.core.user.command.application.port.`in`.result.ConfirmIdentityVerificationResult

interface ConfirmIdentityVerificationUseCase {
	fun confirm(userId: Long, command: ConfirmIdentityVerificationCommand): ConfirmIdentityVerificationResult
}
```

- [ ] **Step 7: 컴파일 확인 + 커밋**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port
git commit -m "feat(user): 본인확인 유스케이스·포트·command/result 정의"
```

---

### Task 4: RegisterIdentityVerificationService

거래등록을 호출하고 REQUESTED 본인확인을 저장한 뒤 프론트용 결과를 반환한다.

**Files:**
- Create: `oneulsogae-core/.../command/application/RegisterIdentityVerificationService.kt`

**Interfaces:**
- Consumes: `KcpCertRegisterPort`, `SaveIdentityVerificationPort`(Task 3), `IdentityVerification`(Task 2).
- Produces: `RegisterIdentityVerificationUseCase` 구현 빈. 동작은 Task 9 E2E로 검증(서비스 유닛테스트 없음 — 프로젝트 컨벤션).

- [ ] **Step 1: 서비스 작성**

```kotlin
package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.user.command.application.port.`in`.RegisterIdentityVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.result.RegisterIdentityVerificationResult
import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterCommand
import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterResult
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveIdentityVerificationPort
import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RegisterIdentityVerificationService(
	private val kcpCertRegisterPort: KcpCertRegisterPort,
	private val saveIdentityVerificationPort: SaveIdentityVerificationPort,
) : RegisterIdentityVerificationUseCase {

	@Transactional
	override fun register(userId: Long): RegisterIdentityVerificationResult {
		val ordrIdxx: String = generateOrderId()
		val result: CertRegisterResult = kcpCertRegisterPort.register(CertRegisterCommand(ordrIdxx))

		saveIdentityVerificationPort.save(
			IdentityVerification.request(userId = userId, ordrIdxx = ordrIdxx, regCertKey = result.regCertKey),
		)

		return RegisterIdentityVerificationResult(
			callUrl = result.callUrl,
			regCertKey = result.regCertKey,
			ordrIdxx = ordrIdxx,
		)
	}

	/** KCP 주문번호(최대 50자). 거래 추적용 유니크 값. */
	private fun generateOrderId(): String =
		"MPL" + UUID.randomUUID().toString().replace("-", "")
}
```

- [ ] **Step 2: 컴파일 확인 + 커밋**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/RegisterIdentityVerificationService.kt
git commit -m "feat(user): 본인확인 거래등록 서비스 추가"
```

---

### Task 5: ConfirmIdentityVerificationService

결과조회→검증값 확정→중복 차단→저장→프로필 반영→상태 전이.

**Files:**
- Create: `oneulsogae-core/.../command/application/ConfirmIdentityVerificationService.kt`

**Interfaces:**
- Consumes: `GetIdentityVerificationPort`, `KcpCertQueryPort`, `ExistsIdentityByDiPort`, `SaveIdentityVerificationPort`(Task 3); `GetUserPort`, `SaveUserPort`, `GetUserDetailPort`, `SaveUserDetailPort`(기존); `TimeGenerator`(기존); `User.passIdentityVerification`(Task 1); `UserDetail.create`(기존).
- Produces: `ConfirmIdentityVerificationUseCase` 구현. 동작은 Task 9 E2E로 검증.

- [ ] **Step 1: 서비스 작성**

```kotlin
package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.ConfirmIdentityVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.command.ConfirmIdentityVerificationCommand
import com.org.oneulsogae.core.user.command.application.port.`in`.result.ConfirmIdentityVerificationResult
import com.org.oneulsogae.core.user.command.application.port.out.ExistsIdentityByDiPort
import com.org.oneulsogae.core.user.command.application.port.out.GetIdentityVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertQueryPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveIdentityVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUserPort
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import com.org.oneulsogae.core.user.command.domain.User
import com.org.oneulsogae.core.user.command.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ConfirmIdentityVerificationService(
	private val getIdentityVerificationPort: GetIdentityVerificationPort,
	private val saveIdentityVerificationPort: SaveIdentityVerificationPort,
	private val kcpCertQueryPort: KcpCertQueryPort,
	private val existsIdentityByDiPort: ExistsIdentityByDiPort,
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val timeGenerator: TimeGenerator,
) : ConfirmIdentityVerificationUseCase {

	@Transactional
	override fun confirm(userId: Long, command: ConfirmIdentityVerificationCommand): ConfirmIdentityVerificationResult {
		val now: LocalDateTime = timeGenerator.now()

		val verification: IdentityVerification = getIdentityVerificationPort.findLatestByUserId(userId)
			?: throw BusinessException(UserErrorCode.IDENTITY_VERIFICATION_NOT_FOUND)
		verification.validateForConfirm(command.regCertKey, command.ordrIdxx)

		val certified: CertifiedIdentity = kcpCertQueryPort.query(command.regCertKey, command.ordrIdxx)

		if (existsIdentityByDiPort.existsVerifiedByDiOnOtherUser(certified.di, userId)) {
			throw BusinessException(UserErrorCode.IDENTITY_ALREADY_REGISTERED)
		}

		saveIdentityVerificationPort.save(verification.complete(certified, now.toLocalDate(), now))

		reflectToUserDetail(userId, certified)
		passIdentityVerification(userId)

		return ConfirmIdentityVerificationResult(
			name = certified.realName,
			adult = certified.isAdult(now.toLocalDate()),
		)
	}

	/** 검증된 생년월일·성별·전화번호를 신뢰값으로 프로필에 반영한다. (프로필이 없으면 생성) */
	private fun reflectToUserDetail(userId: Long, certified: CertifiedIdentity) {
		val detail: UserDetail = getUserDetailPort.findByUserId(userId) ?: UserDetail.create(userId)
		saveUserDetailPort.save(
			detail.copy(
				birthday = certified.birthday,
				gender = certified.gender,
				phoneNumber = certified.phoneNumber,
			),
		)
	}

	/** 본인확인 대기 상태면 온보딩으로 전이한다. (재확정 시 상태 유지) */
	private fun passIdentityVerification(userId: Long) {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")
		if (user.status == UserStatus.IDENTITY_VERIFICATION_PENDING) {
			saveUserPort.save(user.passIdentityVerification())
		}
	}
}
```

- [ ] **Step 2: 컴파일 확인 + 커밋**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/ConfirmIdentityVerificationService.kt
git commit -m "feat(user): 본인확인 결과확정 서비스(중복차단·프로필반영·상태전이) 추가"
```

---

### Task 6: 영속성 어댑터 (엔티티·매퍼·리포지토리·어댑터·CI 암호화)

DB 저장/조회/중복검사 out-port를 구현하고, CI를 앱단 AES-GCM으로 암호화 저장한다.

**Files:** (경로 접두 `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra`)
- Create: `.../user/command/entity/IdentityVerificationEntity.kt`
- Create: `.../user/command/mapper/IdentityVerificationMapper.kt`
- Create: `.../user/command/repository/IdentityVerificationJpaRepository.kt`
- Create: `.../user/command/adapter/IdentityVerificationRepositoryAdapter.kt`
- Create: `.../config/IdentityCryptoProperties.kt`
- Create: `.../user/command/crypto/CiCipher.kt`

**Interfaces:**
- Consumes: `SaveIdentityVerificationPort`, `GetIdentityVerificationPort`, `ExistsIdentityByDiPort`(Task 3); `IdentityVerification`, `IdentityVerificationStatus`(Task 2).
- Produces: `IdentityVerificationEntity`(테이블 `identity_verifications`), `CiCipher.encrypt(plain): String`, 세 out-port 구현 빈.

- [ ] **Step 1: IdentityCryptoProperties**

```kotlin
package com.org.oneulsogae.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** CI 앱단 암호화 키. (@ConfigurationPropertiesScan으로 자동 등록) */
@ConfigurationProperties(prefix = "app.identity")
data class IdentityCryptoProperties(
	val ciEncryptionKey: String = "",
)
```

- [ ] **Step 2: CiCipher (AES-GCM, encrypt-only)**

```kotlin
package com.org.oneulsogae.infra.user.command.crypto

import com.org.oneulsogae.infra.config.IdentityCryptoProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CI 저장용 암호화기. 설정 키를 SHA-256으로 256bit AES 키로 파생하고 AES/GCM/NoPadding으로 암호화한다.
 * 결과는 Base64(iv(12) + ciphertext+tag). CI는 조회 로직에 쓰이지 않아 복호화는 제공하지 않는다(필요 시 확장).
 */
@Component
class CiCipher(properties: IdentityCryptoProperties) {

	private val keySpec: SecretKeySpec = SecretKeySpec(
		MessageDigest.getInstance("SHA-256").digest(properties.ciEncryptionKey.toByteArray(Charsets.UTF_8)),
		"AES",
	)
	private val random: SecureRandom = SecureRandom()

	fun encrypt(plain: String): String {
		val iv: ByteArray = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
		val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, iv))
		val cipherText: ByteArray = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
		return Base64.getEncoder().encodeToString(iv + cipherText)
	}

	companion object {
		private const val IV_LENGTH: Int = 12
		private const val TAG_BITS: Int = 128
	}
}
```

- [ ] **Step 3: 엔티티**

```kotlin
package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "identity_verifications",
	indexes = [
		Index(name = "idx_iv_user_id", columnList = "user_id"),
		Index(name = "idx_iv_di", columnList = "di"),
	],
)
class IdentityVerificationEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "ordr_idxx", nullable = false, length = 50)
	val ordrIdxx: String,

	@Column(name = "reg_cert_key", nullable = false, length = 100)
	val regCertKey: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: IdentityVerificationStatus,

	@Column(name = "real_name")
	var realName: String? = null,

	@Column(name = "birthday")
	var birthday: LocalDate? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", columnDefinition = "varchar(20)")
	var gender: Gender? = null,

	@Column(name = "phone_number", length = 20)
	var phoneNumber: String? = null,

	@Column(name = "di", length = 100)
	var di: String? = null,

	@Column(name = "ci_encrypted", length = 512)
	var ciEncrypted: String? = null,

	@Column(name = "foreigner")
	var foreigner: Boolean? = null,

	@Column(name = "telecom", length = 20)
	var telecom: String? = null,

	@Column(name = "verified_at")
	var verifiedAt: LocalDateTime? = null,
) : BaseEntity()
```

- [ ] **Step 4: 매퍼** (CI 암호화는 어댑터가 넘긴 `CiCipher`로 수행. 복호화 안 하므로 `toDomain`의 `ci`는 null)

```kotlin
package com.org.oneulsogae.infra.user.command.mapper

import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import com.org.oneulsogae.infra.user.command.crypto.CiCipher
import com.org.oneulsogae.infra.user.command.entity.IdentityVerificationEntity

fun IdentityVerificationEntity.toDomain(): IdentityVerification =
	IdentityVerification(
		id = id ?: 0,
		userId = userId,
		ordrIdxx = ordrIdxx,
		regCertKey = regCertKey,
		status = status,
		realName = realName,
		birthday = birthday,
		gender = gender,
		phoneNumber = phoneNumber,
		ci = null,
		di = di,
		foreigner = foreigner,
		telecom = telecom,
		verifiedAt = verifiedAt,
	)

fun IdentityVerification.toEntity(ciCipher: CiCipher): IdentityVerificationEntity =
	IdentityVerificationEntity(
		userId = userId,
		ordrIdxx = ordrIdxx,
		regCertKey = regCertKey,
		status = status,
		realName = realName,
		birthday = birthday,
		gender = gender,
		phoneNumber = phoneNumber,
		di = di,
		ciEncrypted = ci?.let { ciCipher.encrypt(it) },
		foreigner = foreigner,
		telecom = telecom,
		verifiedAt = verifiedAt,
	).also { if (id != 0L) it.id = id }
```

- [ ] **Step 5: 리포지토리**

```kotlin
package com.org.oneulsogae.infra.user.command.repository

import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.IdentityVerificationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IdentityVerificationJpaRepository : JpaRepository<IdentityVerificationEntity, Long> {

	fun findFirstByUserIdOrderByIdDesc(userId: Long): IdentityVerificationEntity?

	fun existsByDiAndStatusAndUserIdNot(
		di: String,
		status: IdentityVerificationStatus,
		userId: Long,
	): Boolean
}
```

- [ ] **Step 6: 어댑터**

```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.out.ExistsIdentityByDiPort
import com.org.oneulsogae.core.user.command.application.port.out.GetIdentityVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveIdentityVerificationPort
import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.crypto.CiCipher
import com.org.oneulsogae.infra.user.command.mapper.toDomain
import com.org.oneulsogae.infra.user.command.mapper.toEntity
import com.org.oneulsogae.infra.user.command.repository.IdentityVerificationJpaRepository
import org.springframework.stereotype.Component

@Component
class IdentityVerificationRepositoryAdapter(
	private val identityVerificationJpaRepository: IdentityVerificationJpaRepository,
	private val ciCipher: CiCipher,
) : SaveIdentityVerificationPort, GetIdentityVerificationPort, ExistsIdentityByDiPort {

	override fun save(verification: IdentityVerification): IdentityVerification =
		identityVerificationJpaRepository.save(verification.toEntity(ciCipher)).toDomain()

	override fun findLatestByUserId(userId: Long): IdentityVerification? =
		identityVerificationJpaRepository.findFirstByUserIdOrderByIdDesc(userId)?.toDomain()

	override fun existsVerifiedByDiOnOtherUser(di: String, userId: Long): Boolean =
		identityVerificationJpaRepository.existsByDiAndStatusAndUserIdNot(
			di, IdentityVerificationStatus.VERIFIED, userId,
		)
}
```

- [ ] **Step 7: 컴파일 확인 + 커밋**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/entity/IdentityVerificationEntity.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/mapper/IdentityVerificationMapper.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/repository/IdentityVerificationJpaRepository.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/IdentityVerificationRepositoryAdapter.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/config/IdentityCryptoProperties.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/crypto/CiCipher.kt
git commit -m "feat(user): 본인확인 영속성 어댑터·CI 암호화 저장 추가"
```

---

### Task 7: KCP HTTP 어댑터 + 암호화 stub + 설정

RestClient로 KCP 거래등록/결과조회를 호출하고, 암호화는 passthrough stub으로 격리한다.

**Files:** (경로 접두 `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra`)
- Create: `.../config/KcpProperties.kt`
- Create: `.../config/KcpConfig.kt`
- Create: `.../user/command/adapter/KcpCertCryptoStubAdapter.kt`
- Create: `.../user/command/adapter/KcpCertRegisterAdapter.kt`
- Create: `.../user/command/adapter/KcpCertQueryAdapter.kt`
- Modify: `oneulsogae-api/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `KcpCertRegisterPort`, `KcpCertQueryPort`, `KcpCertCryptoPort`(Task 3); `CertifiedIdentity`(Task 2); `Gender`(common).
- Produces: `KcpProperties`(`app.kcp.*`), `RestClient`(baseUrl=KCP) 빈, 세 어댑터 빈.

- [ ] **Step 1: KcpProperties**

```kotlin
package com.org.oneulsogae.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.kcp")
data class KcpProperties(
	val siteCd: String = "",
	val encKey: String = "",
	val webSiteId: String = "",
	val baseUrl: String = "https://testcert.kcp.co.kr",
	val retUrl: String = "",
)
```

- [ ] **Step 2: RestClient 빈**

```kotlin
package com.org.oneulsogae.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class KcpConfig {

	@Bean
	fun kcpRestClient(properties: KcpProperties): RestClient =
		RestClient.builder()
			.baseUrl(properties.baseUrl)
			.build()
}
```

- [ ] **Step 3: 암호화 stub 어댑터**

```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.out.KcpCertCryptoPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * KCP 암호화 stub. 공식 라이브러리(encrypJson/decryptJson) 확보 전까지 평문을 그대로 통과시킨다.
 * 실 KCP 서버는 평문을 거부하므로, 이 stub은 WireMock/페이크 대상 테스트에서만 유효하다.
 * TODO: KCP 공식 Java 라이브러리로 교체(ENC_KEY 사용).
 */
@Component
class KcpCertCryptoStubAdapter : KcpCertCryptoPort {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun encryptRegisterData(plainJson: String): String {
		log.warn("[KCP 암호화 미구현 - stub] 평문 통과. 실 연동 전까지만 사용.")
		return plainJson
	}

	override fun decryptCertData(encCertData: String): String {
		log.warn("[KCP 복호화 미구현 - stub] 입력 통과. 실 연동 전까지만 사용.")
		return encCertData
	}
}
```

- [ ] **Step 4: 거래등록 어댑터**

```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.fasterxml.jackson.annotation.JsonProperty
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterCommand
import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterResult
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertCryptoPort
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.oneulsogae.infra.config.KcpProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * KCP 거래등록 어댑터. (POST {baseUrl}/api/reg/certDataReg.do)
 * 요청 본문 파라미터 상세는 KCP API Reference(/reference/regist) 확정본으로 실연동 시 재확인한다.
 */
@Component
class KcpCertRegisterAdapter(
	private val kcpRestClient: RestClient,
	private val kcpProperties: KcpProperties,
	private val kcpCertCryptoPort: KcpCertCryptoPort,
) : KcpCertRegisterPort {

	override fun register(command: CertRegisterCommand): CertRegisterResult {
		val plainJson: String = """
			{"site_cd":"${kcpProperties.siteCd}","ordr_idxx":"${command.ordrIdxx}",""" +
			""""web_siteid":"${kcpProperties.webSiteId}","Ret_URL":"${kcpProperties.retUrl}"}"""
		val encData: String = kcpCertCryptoPort.encryptRegisterData(plainJson.trimIndent())

		val response: KcpRegisterResponse = kcpRestClient.post()
			.uri("/api/reg/certDataReg.do")
			.body(
				mapOf(
					"site_cd" to kcpProperties.siteCd,
					"ordr_idxx" to command.ordrIdxx,
					"enc_data" to encData,
				),
			)
			.retrieve()
			.body<KcpRegisterResponse>()
			?: throw BusinessException(UserErrorCode.KCP_REGISTER_FAILED)

		if (response.resCd != SUCCESS_CODE || response.regCertKey == null || response.callUrl == null) {
			throw BusinessException(UserErrorCode.KCP_REGISTER_FAILED, "res_cd=${response.resCd}, res_msg=${response.resMsg}")
		}
		return CertRegisterResult(regCertKey = response.regCertKey, callUrl = response.callUrl)
	}

	companion object {
		private const val SUCCESS_CODE: String = "0000"
	}
}

data class KcpRegisterResponse(
	@JsonProperty("res_cd") val resCd: String?,
	@JsonProperty("res_msg") val resMsg: String?,
	@JsonProperty("reg_cert_key") val regCertKey: String?,
	@JsonProperty("call_url") val callUrl: String?,
)
```

- [ ] **Step 5: 결과조회 어댑터** (복호화 + KCP 필드 매핑 → CertifiedIdentity)

```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertCryptoPort
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertQueryPort
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import com.org.oneulsogae.infra.config.KcpProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * KCP 결과조회 어댑터. (POST {baseUrl}/api/query/getCertData.do)
 * enc_cert_data 복호화 후 KCP 필드를 [CertifiedIdentity]로 매핑한다.
 * 복호화 결과 필드명/코드값(sex_code, local_flag 등)은 KCP 결과 매뉴얼 확정본으로 실연동 시 재확인한다.
 */
@Component
class KcpCertQueryAdapter(
	private val kcpRestClient: RestClient,
	private val kcpProperties: KcpProperties,
	private val kcpCertCryptoPort: KcpCertCryptoPort,
	private val objectMapper: ObjectMapper,
) : KcpCertQueryPort {

	override fun query(regCertKey: String, ordrIdxx: String): CertifiedIdentity {
		val response: KcpQueryResponse = kcpRestClient.post()
			.uri("/api/query/getCertData.do")
			.contentType(MediaType.APPLICATION_JSON)
			.body(mapOf("reg_cert_key" to regCertKey, "ordr_idxx" to ordrIdxx))
			.retrieve()
			.body<KcpQueryResponse>()
			?: throw BusinessException(UserErrorCode.KCP_QUERY_FAILED)

		if (response.resCd != SUCCESS_CODE || response.encCertData == null) {
			throw BusinessException(UserErrorCode.KCP_QUERY_FAILED, "res_cd=${response.resCd}, res_msg=${response.resMsg}")
		}

		val decrypted: String = kcpCertCryptoPort.decryptCertData(response.encCertData)
		val data: KcpCertData = objectMapper.readValue(decrypted, KcpCertData::class.java)
		return data.toCertifiedIdentity()
	}

	companion object {
		private const val SUCCESS_CODE: String = "0000"
		private val BIRTHDAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
	}

	private fun KcpCertData.toCertifiedIdentity(): CertifiedIdentity =
		CertifiedIdentity(
			realName = userName,
			birthday = LocalDate.parse(birthDay, BIRTHDAY_FORMAT),
			gender = if (sexCode == MALE_CODE) Gender.MALE else Gender.FEMALE,
			phoneNumber = phoneNo,
			ci = ci,
			di = di,
			foreigner = localFlag != LOCAL_CODE,
			telecom = commId,
		)
}

private const val MALE_CODE: String = "01"
private const val LOCAL_CODE: String = "01"

data class KcpQueryResponse(
	@JsonProperty("res_cd") val resCd: String?,
	@JsonProperty("res_msg") val resMsg: String?,
	@JsonProperty("enc_cert_data") val encCertData: String?,
)

/** KCP 복호화 결과(dec_data). 필드명은 KCP 결과 매뉴얼 기준(실연동 시 확정). */
data class KcpCertData(
	@JsonProperty("user_name") val userName: String,
	@JsonProperty("birth_day") val birthDay: String,
	@JsonProperty("sex_code") val sexCode: String,
	@JsonProperty("phone_no") val phoneNo: String,
	@JsonProperty("ci") val ci: String,
	@JsonProperty("di") val di: String,
	@JsonProperty("local_flag") val localFlag: String,
	@JsonProperty("comm_id") val commId: String,
)
```

- [ ] **Step 6: application.yml — app.kcp / app.identity 추가**

`oneulsogae-api/src/main/resources/application.yml`의 `app:` 블록(`s3:` 아래)에 추가:

```yaml
  kcp:
    site-cd: ${KCP_SITE_CD:}
    enc-key: ${KCP_ENC_KEY:}
    web-siteid: ${KCP_WEB_SITEID:}
    base-url: ${KCP_BASE_URL:https://testcert.kcp.co.kr}
    ret-url: ${KCP_RET_URL:}
  identity:
    ci-encryption-key: ${IDENTITY_CI_KEY:local-dev-ci-key-change-me}
```

- [ ] **Step 7: 컴파일 확인 + 커밋**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/config/KcpProperties.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/config/KcpConfig.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/KcpCertCryptoStubAdapter.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/KcpCertRegisterAdapter.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/KcpCertQueryAdapter.kt \
  oneulsogae-api/src/main/resources/application.yml
git commit -m "feat(user): KCP 거래등록·결과조회 HTTP 어댑터와 암호화 stub·설정 추가"
```

---

### Task 8: API 컨트롤러 + DTO

`/users/v1/identity-verification`의 register/confirm 엔드포인트를 추가한다.

**Files:** (경로 접두 `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user`)
- Create: `IdentityVerificationController.kt`
- Create: `request/ConfirmIdentityVerificationRequest.kt`
- Create: `response/RegisterIdentityVerificationResponse.kt`
- Create: `response/ConfirmIdentityVerificationResponse.kt`

**Interfaces:**
- Consumes: `RegisterIdentityVerificationUseCase`, `ConfirmIdentityVerificationUseCase`(Task 3·4·5); `AuthUser`/`LoginUser`; `ApiResponse`.
- Produces: `POST /users/v1/identity-verification/register`, `POST /users/v1/identity-verification/confirm`.

- [ ] **Step 1: request DTO**

```kotlin
package com.org.oneulsogae.api.user.request

import com.org.oneulsogae.core.user.command.application.port.`in`.command.ConfirmIdentityVerificationCommand
import jakarta.validation.constraints.NotBlank

data class ConfirmIdentityVerificationRequest(
	@field:NotBlank(message = "regCertKey는 필수입니다.")
	val regCertKey: String,

	@field:NotBlank(message = "ordrIdxx는 필수입니다.")
	val ordrIdxx: String,
) {
	fun toCommand(): ConfirmIdentityVerificationCommand =
		ConfirmIdentityVerificationCommand(regCertKey = regCertKey, ordrIdxx = ordrIdxx)
}
```

- [ ] **Step 2: response DTO**

```kotlin
package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.core.user.command.application.port.`in`.result.RegisterIdentityVerificationResult

data class RegisterIdentityVerificationResponse(
	val callUrl: String,
	val regCertKey: String,
	val ordrIdxx: String,
) {
	companion object {
		fun of(result: RegisterIdentityVerificationResult): RegisterIdentityVerificationResponse =
			RegisterIdentityVerificationResponse(
				callUrl = result.callUrl,
				regCertKey = result.regCertKey,
				ordrIdxx = result.ordrIdxx,
			)
	}
}
```

```kotlin
package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.core.user.command.application.port.`in`.result.ConfirmIdentityVerificationResult

data class ConfirmIdentityVerificationResponse(
	val name: String,
	val adult: Boolean,
) {
	companion object {
		fun of(result: ConfirmIdentityVerificationResult): ConfirmIdentityVerificationResponse =
			ConfirmIdentityVerificationResponse(name = result.name, adult = result.adult)
	}
}
```

- [ ] **Step 3: 컨트롤러**

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.api.user.request.ConfirmIdentityVerificationRequest
import com.org.oneulsogae.api.user.response.ConfirmIdentityVerificationResponse
import com.org.oneulsogae.api.user.response.RegisterIdentityVerificationResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.ConfirmIdentityVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.RegisterIdentityVerificationUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/v1/identity-verification")
@Tag(name = "본인확인(KCP)", description = "온보딩 본인확인 엔드포인트 (모두 인증 필요)")
class IdentityVerificationController(
	private val registerIdentityVerificationUseCase: RegisterIdentityVerificationUseCase,
	private val confirmIdentityVerificationUseCase: ConfirmIdentityVerificationUseCase,
) {

	@Operation(summary = "본인확인 거래등록", description = "KCP 인증창 호출용 callUrl·regCertKey·ordrIdxx를 반환한다.")
	@PostMapping("/register")
	fun register(
		@LoginUser user: AuthUser,
	): ApiResponse<RegisterIdentityVerificationResponse> =
		ApiResponse.success(
			RegisterIdentityVerificationResponse.of(registerIdentityVerificationUseCase.register(user.id)),
		)

	@Operation(summary = "본인확인 결과 확정", description = "인증창 결과의 regCertKey·ordrIdxx로 결과를 조회·검증·저장한다.")
	@PostMapping("/confirm")
	fun confirm(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: ConfirmIdentityVerificationRequest,
	): ApiResponse<ConfirmIdentityVerificationResponse> =
		ApiResponse.success(
			ConfirmIdentityVerificationResponse.of(
				confirmIdentityVerificationUseCase.confirm(user.id, request.toCommand()),
			),
		)
}
```

- [ ] **Step 4: 전체 컴파일 확인 + 커밋**

Run: `./gradlew :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/IdentityVerificationController.kt \
  oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/request/ConfirmIdentityVerificationRequest.kt \
  oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/response/RegisterIdentityVerificationResponse.kt \
  oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/response/ConfirmIdentityVerificationResponse.kt
git commit -m "feat(user): 본인확인 register/confirm 컨트롤러·DTO 추가"
```

---

### Task 9: E2E — 테스트 더블 + register/confirm 통합 테스트

KCP 아웃포트를 페이크로 대체(`TestFileStorageConfig` 패턴)하고, register→confirm 플로우와 성인/중복/위변조 케이스를 검증한다.

**Files:**
- Create: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/IdentityVerificationEntityFixture.kt`
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/config/TestKcpConfig.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/integration/AbstractIntegrationSupport.kt` (@Import에 `TestKcpConfig` 추가)
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/IdentityVerificationE2ESupport.kt`
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/IdentityVerificationE2ETest.kt`

**Interfaces:**
- Consumes: 전 Task 산출물; `IntegrationUtil.persist/getQuery`; `UserEntityFixture`; `RestAssuredDsl`.
- Produces: `TestKcpConfig`(페이크 `KcpCertRegisterPort`/`KcpCertQueryPort`), `object FakeKcpCertData { var next: CertifiedIdentity? }`, `IdentityVerificationEntityFixture.create(...)`.

- [ ] **Step 1: 엔티티 픽스처** — `IdentityVerificationEntityFixture.kt`

```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.IdentityVerificationEntity
import java.time.LocalDate
import java.time.LocalDateTime

object IdentityVerificationEntityFixture {

	fun create(
		userId: Long = 1L,
		ordrIdxx: String = "ORD-FIX",
		regCertKey: String = "REG-FIX",
		status: IdentityVerificationStatus = IdentityVerificationStatus.VERIFIED,
		di: String? = "DI-FIX",
		birthday: LocalDate? = LocalDate.of(1996, 1, 1),
		gender: Gender? = Gender.MALE,
		verifiedAt: LocalDateTime? = LocalDateTime.of(2026, 7, 9, 12, 0),
	): IdentityVerificationEntity =
		IdentityVerificationEntity(
			userId = userId,
			ordrIdxx = ordrIdxx,
			regCertKey = regCertKey,
			status = status,
			realName = "홍길동",
			birthday = birthday,
			gender = gender,
			phoneNumber = "01012345678",
			di = di,
			ciEncrypted = "enc",
			foreigner = false,
			telecom = "SKT",
			verifiedAt = verifiedAt,
		)
}
```

- [ ] **Step 2: 테스트 더블 설정** — `TestKcpConfig.kt`

```kotlin
package com.org.oneulsogae.common.config

import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterResult
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertQueryPort
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트에서 KCP 아웃포트를 페이크로 대체한다. (실 HTTP·암호화 미호출)
 * - register: 결정적 regCertKey/callUrl 반환.
 * - query: [FakeKcpCertData.next]에 세팅한 검증값을 반환(테스트가 성인/미성년/DI를 제어).
 * [AbstractIntegrationSupport]에 등록돼 모든 통합 테스트가 단일 컨텍스트를 공유한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestKcpConfig {

	@Bean
	@Primary
	fun fakeKcpCertRegisterPort(): KcpCertRegisterPort =
		KcpCertRegisterPort { command ->
			CertRegisterResult(
				regCertKey = "TEST-REG-${command.ordrIdxx}",
				callUrl = "https://testcert.kcp.co.kr/cert?regCertKey=TEST-REG-${command.ordrIdxx}",
			)
		}

	@Bean
	@Primary
	fun fakeKcpCertQueryPort(): KcpCertQueryPort =
		KcpCertQueryPort { _, _ ->
			FakeKcpCertData.next ?: error("FakeKcpCertData.next 미설정 — 테스트에서 먼저 세팅하세요.")
		}
}

object FakeKcpCertData {
	var next: CertifiedIdentity? = null
}
```

- [ ] **Step 3: AbstractIntegrationSupport @Import에 추가**

`@Import(...)` 목록(60행 부근)에 `TestKcpConfig::class`를 추가한다. 예:
```kotlin
@Import(
	TestDatabaseContainersConfig::class,
	TestRedisContainersConfig::class,
	TestWireMockConfig::class,
	TestRegionShufflerConfig::class,
	TestFileStorageConfig::class,
	TestKcpConfig::class,
	IntegrationUtil::class,
)
```
(기존 목록 그대로 두고 `TestKcpConfig::class` 한 줄만 추가. import 문 `com.org.oneulsogae.common.config.TestKcpConfig`도 추가.)

- [ ] **Step 4: E2E 헬퍼** — `IdentityVerificationE2ESupport.kt`

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.user.command.entity.QIdentityVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity

private val user: QUserEntity = QUserEntity.userEntity
private val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
private val identity: QIdentityVerificationEntity = QIdentityVerificationEntity.identityVerificationEntity

internal fun cleanupIdentity() {
	IntegrationUtil.deleteAll(identity)
	IntegrationUtil.deleteAll(detail)
	IntegrationUtil.deleteAll(user)
}

internal fun userStatusOfIdentity(userId: Long): UserStatus =
	IntegrationUtil.getQuery().select(user.status).from(user).where(user.id.eq(userId)).fetchOne()!!

internal fun latestIdentityStatusOf(userId: Long): IdentityVerificationStatus =
	IntegrationUtil.getQuery()
		.select(identity.status).from(identity)
		.where(identity.userId.eq(userId))
		.orderBy(identity.id.desc())
		.fetchFirst()!!
```
(Q-타입 패키지/이름이 다르면 실제 생성된 Q클래스에 맞춘다. `deleteAll`/`getQuery`는 `IntegrationUtil` 제공.)

- [ ] **Step 5: E2E 테스트 작성** — `IdentityVerificationE2ETest.kt`

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.config.FakeKcpCertData
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.fixture.IdentityVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.notNullValue
import java.time.LocalDate

class IdentityVerificationE2ETest : AbstractIntegrationSupport({

	fun adult(di: String = "DI-UNIQUE"): CertifiedIdentity =
		CertifiedIdentity(
			realName = "홍길동", birthday = LocalDate.of(1996, 1, 1), gender = Gender.MALE,
			phoneNumber = "01012345678", ci = "CI-$di", di = di, foreigner = false, telecom = "SKT",
		)

	fun registerFor(userId: Long): Pair<String, String> {
		val response: ValidatableResponse = post("/users/v1/identity-verification/register") {
			bearer(accessTokenFor(userId))
		}
		response expect {
			status(200)
			body("data.callUrl", notNullValue())
			body("data.regCertKey", notNullValue())
			body("data.ordrIdxx", notNullValue())
		}
		val regCertKey: String = response.extract().path("data.regCertKey")
		val ordrIdxx: String = response.extract().path("data.ordrIdxx")
		return regCertKey to ordrIdxx
	}

	describe("POST /users/v1/identity-verification (register→confirm)") {

		context("성인이고 DI가 중복되지 않으면") {
			it("확정되고 사용자가 ONBOARDING으로 전이한다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (regCertKey: String, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult(di = "DI-OK")

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"$regCertKey","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(200)
					body("data.name", "홍길동")
					body("data.adult", true)
				}

				userStatusOfIdentity(userId) shouldBe UserStatus.ONBOARDING
				latestIdentityStatusOf(userId) shouldBe IdentityVerificationStatus.VERIFIED
			}
		}

		context("미성년이면") {
			it("IDENTITY_NOT_ADULT로 거절하고 상태를 유지한다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (regCertKey: String, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult().copy(birthday = LocalDate.of(2012, 1, 1))

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"$regCertKey","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-029")
				}

				userStatusOfIdentity(userId) shouldBe UserStatus.IDENTITY_VERIFICATION_PENDING
			}
		}

		context("같은 DI로 이미 가입한 다른 사용자가 있으면") {
			it("IDENTITY_ALREADY_REGISTERED로 거절한다 (409)") {
				val otherId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "other", status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(
					IdentityVerificationEntityFixture.create(userId = otherId, di = "DI-DUP"),
				)
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (regCertKey: String, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult(di = "DI-DUP")

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"$regCertKey","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(409)
					body("error.code", "USER-030")
				}
			}
		}

		context("거래 정보가 위변조되면") {
			it("IDENTITY_VERIFICATION_MISMATCH로 거절한다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (_, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult()

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"TAMPERED","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(400)
					body("error.code", "USER-027")
				}
			}
		}
	}

	afterTest {
		FakeKcpCertData.next = null
		cleanupIdentity()
	}
})
```

- [ ] **Step 6: 전체 테스트 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.IdentityVerificationE2ETest"`
Expected: PASS (4개 컨텍스트 모두)

- [ ] **Step 7: 전체 회귀 + 커밋**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

```bash
git add oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/IdentityVerificationEntityFixture.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/config/TestKcpConfig.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/integration/AbstractIntegrationSupport.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/IdentityVerificationE2ESupport.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/IdentityVerificationE2ETest.kt
git commit -m "test(user): 본인확인 register/confirm E2E와 KCP 테스트 더블 추가"
```

---

## 프론트엔드 대응 (직접 수정하지 않음 — 안내)

> `meeple-backend`만 수정한다. 아래는 프론트가 대응해야 할 내용이다.

- **KCP 인증창 오픈**: `POST /users/v1/identity-verification/register` 응답 `{ callUrl, regCertKey, ordrIdxx }`로 인증창 호출(웹=팝업, 모바일=페이지전환/웹뷰). KCP `Ret_URL`은 `app.kcp.ret-url`(백엔드 설정)로 지정된 **프론트 결과 페이지**.
- **결과 확정**: Ret_URL 페이지에서 KCP 결과 수신 후 `POST /users/v1/identity-verification/confirm { regCertKey, ordrIdxx }` 호출 → `{ name, adult }` 수신.
- **온보딩**: 본인확인 통과(사용자 status `ONBOARDING` 전이) 전 정보입력 차단. 생년월일·성별·전화번호는 확정값 프리필·읽기전용(백엔드가 확정 시 UserDetail에 반영).

## 실연동 전 후속(코드 밖)

- KCP 파트너센터에서 site_cd·ENC_KEY·web_siteid 발급 → 환경변수(`KCP_SITE_CD` 등) 주입, `KCP_BASE_URL`을 운영(`https://cert.kcp.co.kr`)으로.
- `IDENTITY_CI_KEY` 운영 키 발급(현재 yml 기본값은 로컬 전용).
- KCP 공식 Java 라이브러리 확보 → `KcpCertCryptoStubAdapter`를 실 encrypJson/decryptJson 구현으로 교체. 이때 `KcpCertRegisterAdapter`의 요청 본문 파라미터와 `KcpCertData` 필드명/코드값(`sex_code`·`local_flag` 등)을 KCP API Reference로 확정.
- 실 KCP 대상 통합 테스트(WireMock 또는 스테이징)로 어댑터 계층 별도 검증.
- `identity_verifications.di` 유니크 제약을 DB에 걸지(하드 차단) 애플리케이션 검사만 둘지 실DB DDL 반영 시 결정.
```
