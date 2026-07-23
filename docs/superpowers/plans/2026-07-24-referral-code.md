# 추천 코드 기능 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온보딩 시 추천 코드를 입력하면 추천인·신규 유저 모두 50코인을 받는 기능 (백엔드 + 웹 + 모바일).

**Architecture:** 백엔드는 user 도메인 확장(`referral_code`·`referred_by_user_id` 컬럼 2개, 신규 테이블 없음) + 기존 `AcquireCoinUseCase` 재사용. 코드 발급은 조회 시점 lazy get-or-create(command 유스케이스). 웹/모바일은 온보딩 마지막에 선택 스텝 추가 + 마이탭 코인 섹션에 "내 추천 코드" 화면 진입점 추가.

**Tech Stack:** Kotlin/Spring Boot(헥사고날), Kotest E2E(Testcontainers), Next.js 16(App Router, TanStack Query), Expo/React Native(expo-router).

**스펙:** `docs/superpowers/specs/2026-07-24-referral-code-design.md`

## Global Constraints

- 백엔드 응답·주석·커밋 메시지는 한국어. 커밋 형식 `<type>(<domain>): <설명>` (CLAUDE.md).
- 타입 명시: 변수·반환 타입 생략 금지 (백엔드 Kotlin).
- `LocalDateTime.now()` 직접 호출 금지 (이번 작업엔 현재 시각 불필요).
- 보상 수량 50코인 = `CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT`. 코드 형식 = `A-Z0-9` 8자.
- 무효 추천 코드는 조용히 무시 (온보딩 성공, 지급만 없음).
- 각 리포지토리는 별도 git repo. 백엔드 작업은 `/Users/inwookjung/IdeaProjects/oneulsogae-backend`, 웹은 `oneulsogae-frontend`, 모바일은 `oneulsogae-mobile`에서 커밋.
- 운영 DB DDL은 수동 관리(main `application.yml`에 ddl-auto 없음, 테스트만 create-drop). Task 3 완료 시 운영 반영용 DDL을 사용자에게 보고할 것:
  ```sql
  ALTER TABLE users ADD COLUMN referral_code VARCHAR(8) NULL;
  ALTER TABLE users ADD COLUMN referred_by_user_id BIGINT NULL;
  ALTER TABLE users ADD CONSTRAINT ux_referral_code UNIQUE (referral_code);
  ```

---

### Task 1: 코인 상수 (common)

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinGetType.kt`
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinPolicy.kt`

**Interfaces:**
- Produces: `CoinGetType.REFERRAL`, `CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT: Int = 50` (Task 5가 사용)

- [ ] **Step 1: CoinGetType에 REFERRAL 추가**

`SIGNUP("가입 축하"),` 아래에 추가:

```kotlin
	/** 추천 코드 입력으로 추천인·신규 유저 양쪽에 지급하는 코인. */
	REFERRAL("추천 보상"),
```

- [ ] **Step 2: CoinPolicy에 상수 추가**

`SIGNUP_REWARD_COIN_AMOUNT` 아래에 추가:

```kotlin
	/** 추천 코드 보상으로 추천인·신규 유저 각각에게 지급하는 코인 수량. */
	const val REFERRAL_REWARD_COIN_AMOUNT: Int = 50
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-common:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-common
git commit -m "feat(coin): 추천 보상 코인 타입(REFERRAL)·수량(50) 상수 추가"
```

---

### Task 2: user 도메인 모델 (코드 생성 + 추천 판정)

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/ReferralCode.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/User.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/ReferralCodeTest.kt` (도메인 유닛 테스트는 api 모듈 `domain/<도메인>` 패키지에 두는 기존 패턴)

**Interfaces:**
- Produces:
  - `ReferralCode.generate(random: java.util.Random): String` — `A-Z0-9` 8자
  - `User.referralCode: String?`, `User.referredByUserId: Long?` (프로퍼티)
  - `User.assignReferralCode(code: String): User`
  - `User.referredBy(referrerId: Long): User`
  - `User.canRefer(newUserId: Long): Boolean` — ACTIVE(isRegistered) && 본인 아님

- [ ] **Step 1: 실패하는 유닛 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/ReferralCodeTest.kt` 생성 (같은 디렉토리의 기존 테스트 스타일 — Kotest DescribeSpec — 확인 후 동일 스타일로):

```kotlin
package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.domain.ReferralCode
import com.org.oneulsogae.core.user.command.domain.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.util.Random

class ReferralCodeTest : DescribeSpec({

	describe("ReferralCode.generate") {
		it("A-Z·0-9로 이루어진 8자 코드를 생성한다") {
			val code: String = ReferralCode.generate(Random(42L))
			code shouldMatch Regex("^[A-Z0-9]{8}$")
		}

		it("같은 시드면 같은 코드, 다른 시드면 다른 코드를 생성한다") {
			ReferralCode.generate(Random(1L)) shouldBe ReferralCode.generate(Random(1L))
			(ReferralCode.generate(Random(1L)) == ReferralCode.generate(Random(2L))) shouldBe false
		}
	}

	describe("User.canRefer") {
		val referrer = User(id = 10L, provider = "kakao", providerId = "p", status = UserStatus.ACTIVE)

		it("ACTIVE 추천인이 다른 유저를 추천하면 true") {
			referrer.canRefer(newUserId = 20L) shouldBe true
		}

		it("본인을 추천하면 false") {
			referrer.canRefer(newUserId = 10L) shouldBe false
		}

		it("ACTIVE가 아닌 추천인이면 false") {
			referrer.copy(status = UserStatus.ONBOARDING).canRefer(newUserId = 20L) shouldBe false
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.domain.user.ReferralCodeTest'`
Expected: 컴파일 실패 ("unresolved reference: ReferralCode" 등)

- [ ] **Step 3: ReferralCode 오브젝트 구현**

`oneulsogae-core/.../user/command/domain/ReferralCode.kt` 생성:

```kotlin
package com.org.oneulsogae.core.user.command.domain

import java.util.Random

/**
 * 추천 코드 생성 규칙. `A-Z0-9` 8자 랜덤 문자열을 만든다.
 * 난수원은 파라미터로 주입받아 테스트에서 고정할 수 있다. (실사용은 SecureRandom)
 */
object ReferralCode {

	private const val LENGTH: Int = 8
	private const val CHARS: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

	fun generate(random: Random): String =
		buildString(LENGTH) {
			repeat(LENGTH) { append(CHARS[random.nextInt(CHARS.length)]) }
		}
}
```

- [ ] **Step 4: User에 필드·행위 추가**

`User.kt`의 data class 프로퍼티에 추가 (`lastLoginAt` 아래):

```kotlin
	/** 내가 남에게 공유하는 추천 코드. 조회 시점 lazy 발급이라 발급 전엔 null. */
	val referralCode: String? = null,
	/** 나를 추천한(내가 가입 시 코드를 입력한) 추천인 id. 추천 없이 가입하면 null. */
	val referredByUserId: Long? = null,
```

`recordLogin` 아래에 메서드 추가:

```kotlin
	/** 추천 코드를 발급(부여)한다. */
	fun assignReferralCode(code: String): User =
		copy(referralCode = code)

	/** 나를 추천한 추천인을 기록한다. */
	fun referredBy(referrerId: Long): User =
		copy(referredByUserId = referrerId)

	/** 이 유저(추천인)가 해당 신규 유저를 추천해 보상받을 수 있는지 판정한다. (정식 가입 상태 + 본인 아님) */
	fun canRefer(newUserId: Long): Boolean =
		isRegistered && id != newUserId
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.domain.user.ReferralCodeTest'`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core oneulsogae-api/src/test
git commit -m "feat(user): 추천 코드 도메인 모델 추가 (코드 생성 규칙·추천 가능 판정)"
```

---

### Task 3: 인프라 (엔티티·매퍼·리포지토리·포트·어댑터)

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/entity/UserEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/mapper/UserMapper.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/repository/UserJpaRepository.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/out/GetUserPort.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/UserRepositoryAdapter.kt`

**Interfaces:**
- Consumes: `User.referralCode`/`referredByUserId` (Task 2)
- Produces: `GetUserPort.findByReferralCode(code: String): User?` (Task 4·5가 사용)

- [ ] **Step 1: UserEntity 컬럼 추가**

`@Table`의 `uniqueConstraints`에 추가:

```kotlin
	uniqueConstraints = [
		UniqueConstraint(name = "ux_provider_provider_id", columnNames = ["provider", "provider_id"]),
		UniqueConstraint(name = "ux_referral_code", columnNames = ["referral_code"]),
	],
```

생성자 프로퍼티에 추가 (`lastLoginAt` 아래):

```kotlin
	/** 내가 남에게 공유하는 추천 코드. lazy 발급이라 발급 전엔 null. (유니크) */
	@Column(name = "referral_code", length = 8)
	var referralCode: String? = null,

	/** 나를 추천한 추천인 user id. 추천 없이 가입하면 null. (FK 없이 id만 보관) */
	@Column(name = "referred_by_user_id")
	var referredByUserId: Long? = null,
```

- [ ] **Step 2: UserMapper 양방향 반영**

`toDomain()`에 추가:

```kotlin
		referralCode = referralCode,
		referredByUserId = referredByUserId,
```

`toEntity()`에 추가:

```kotlin
		referralCode = referralCode,
		referredByUserId = referredByUserId,
```

- [ ] **Step 3: UserJpaRepository 파생 쿼리 추가**

`findByProviderAndProviderId` 아래에 추가 (referral_code 유니크 인덱스를 타는 동등 조건 단건 조회):

```kotlin
	fun findByReferralCode(referralCode: String): UserEntity?
```

- [ ] **Step 4: GetUserPort에 메서드 추가**

`findById` 아래에 추가:

```kotlin
	/** 추천 코드로 사용자를 조회한다. 없으면 null. */
	fun findByReferralCode(code: String): User?
```

- [ ] **Step 5: UserRepositoryAdapter 구현 추가**

`findById` 오버라이드 아래에 추가:

```kotlin
	override fun findByReferralCode(code: String): User? =
		userJpaRepository.findByReferralCode(code)?.toDomain()
```

- [ ] **Step 6: 전체 컴파일 + 기존 테스트 회귀 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋 + 운영 DDL 보고**

```bash
git add oneulsogae-core oneulsogae-infra
git commit -m "feat(user): users 테이블에 추천 코드·추천인 컬럼 및 조회 포트 추가"
```

Global Constraints의 운영 DDL 3줄을 최종 보고에 포함할 것.

---

### Task 4: 추천 코드 발급 API (GET /users/v1/me/referral-code)

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/in/IssueReferralCodeUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/IssueReferralCodeService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/UserReferralCodeController.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/response/ReferralCodeResponse.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/ReferralCodeE2ETest.kt`

**Interfaces:**
- Consumes: `GetUserPort.findByReferralCode`, `SaveUserPort.save`, `User.assignReferralCode`, `ReferralCode.generate`
- Produces: `IssueReferralCodeUseCase.issue(userId: Long): String` / HTTP `GET /users/v1/me/referral-code` → `{ "referralCode": "AB12CD34" }`

- [ ] **Step 1: 실패하는 E2E 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/ReferralCodeE2ETest.kt` 생성. `get` 헬퍼는 같은 패키지 다른 E2E(`GetMyProfileE2ETest` 등)의 import(`com.org.oneulsogae.common.integration.get`)를 확인해 동일하게 사용:

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

/**
 * `GET /users/v1/me/referral-code` E2E 테스트.
 * 추천 코드 lazy 발급(get-or-create)과 재호출 멱등성을 검증한다.
 */
class ReferralCodeE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/me/referral-code") {

		context("아직 추천 코드가 없는 유저가 조회하면") {
			it("A-Z0-9 8자 코드가 발급되어 저장·반환된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				val code: String = get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
				} path "data.referralCode"

				code shouldMatch Regex("^[A-Z0-9]{8}$")
				referralCodeOf(userId) shouldBe code
			}
		}

		context("이미 코드가 있는 유저가 다시 조회하면") {
			it("같은 코드를 그대로 반환한다") {
				val user = UserEntityFixture.create(status = UserStatus.ACTIVE)
				user.referralCode = "FIXED123"
				val userId: Long = IntegrationUtil.persist(user).id!!

				get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.referralCode", "FIXED123")
				}
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})

/** 저장된 추천 코드를 DB에서 직접 읽는다. */
internal fun referralCodeOf(userId: Long): String? =
	IntegrationUtil.getQuery()
		.select(QUserEntity.userEntity.referralCode)
		.from(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetchOne()
```

주의: 응답 문자열 추출(`path "data.referralCode"`)은 `RestAssuredDsl`이 제공하는 형태를 확인해 맞출 것 — 없으면 `body("data.referralCode", matchesPattern("^[A-Z0-9]{8}$"))` 식 검증 + `referralCodeOf(userId)!! shouldMatch Regex(...)`로 대체.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.api.user.ReferralCodeE2ETest'`
Expected: FAIL (404 — 엔드포인트 없음, 또는 컴파일 실패)

- [ ] **Step 3: 에러 코드 추가**

`UserErrorCode.kt`의 프로필 섹션 아래에 추가 (코드 번호는 파일 전체에서 미사용 번호 확인 후 부여, 예 USER-036이 비어 있으면 다음 번호):

```kotlin
	// 추천 코드
	REFERRAL_CODE_ISSUE_FAILED("USER-040", "추천 코드 발급에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR),
```

- [ ] **Step 4: 인포트·서비스 구현**

`IssueReferralCodeUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`

/**
 * 추천 코드 발급 인포트(유스케이스).
 * 내 추천 코드를 반환하고, 아직 없으면 생성·저장 후 반환한다. (get-or-create, 멱등)
 */
interface IssueReferralCodeUseCase {

	fun issue(userId: Long): String
}
```

`IssueReferralCodeService.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.IssueReferralCodeUseCase
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUserPort
import com.org.oneulsogae.core.user.command.domain.ReferralCode
import com.org.oneulsogae.core.user.command.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

/**
 * [IssueReferralCodeUseCase] 구현.
 * 코드가 이미 있으면 그대로 반환하고, 없으면 생성해 저장한다. (조회처럼 보이지만 쓰기가 발생하는 command)
 */
@Service
class IssueReferralCodeService(
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
) : IssueReferralCodeUseCase {

	private val random: SecureRandom = SecureRandom()

	@Transactional
	override fun issue(userId: Long): String {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")
		user.referralCode?.let { return it }

		// ponytail: 생성→중복조회→저장. 동시 발급 레이스는 ux_referral_code 유니크 제약이 최종 방어(드물게 실패 시 재요청).
		repeat(MAX_GENERATE_ATTEMPTS) {
			val code: String = ReferralCode.generate(random)
			if (getUserPort.findByReferralCode(code) == null) {
				saveUserPort.save(user.assignReferralCode(code))
				return code
			}
		}
		throw BusinessException(UserErrorCode.REFERRAL_CODE_ISSUE_FAILED)
	}

	companion object {
		/** 36^8 공간이라 충돌은 사실상 없지만, 무한 루프 방지 상한. */
		private const val MAX_GENERATE_ATTEMPTS: Int = 5
	}
}
```

- [ ] **Step 5: 응답 DTO·컨트롤러 구현**

`ReferralCodeResponse.kt`:

```kotlin
package com.org.oneulsogae.api.user.response

/** 내 추천 코드 조회 응답. */
data class ReferralCodeResponse(
	val referralCode: String,
)
```

`UserReferralCodeController.kt`:

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.api.user.response.ReferralCodeResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.IssueReferralCodeUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 추천 코드 엔드포인트. (인증 필요)
 * - GET /: 내 추천 코드를 반환한다. 아직 없으면 발급(get-or-create)해 반환한다.
 */
@RestController
@RequestMapping("/users/v1/me/referral-code")
@Tag(name = "유저 추천 코드", description = "내 추천 코드 조회(없으면 발급) 엔드포인트 (인증 필요)")
class UserReferralCodeController(
	private val issueReferralCodeUseCase: IssueReferralCodeUseCase,
) {

	/** 내 추천 코드를 반환한다. 아직 없으면 발급해 저장 후 반환한다. (멱등) */
	@Operation(summary = "내 추천 코드 조회", description = "내 추천 코드를 반환한다. 아직 없으면 발급(get-or-create)해 반환한다.")
	@GetMapping
	fun getMyReferralCode(
		@LoginUser user: AuthUser,
	): ApiResponse<ReferralCodeResponse> =
		ApiResponse.success(ReferralCodeResponse(issueReferralCodeUseCase.issue(user.id)))
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.api.user.ReferralCodeE2ETest'`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-core oneulsogae-api
git commit -m "feat(user): 내 추천 코드 조회(없으면 발급) API 추가 (GET /users/v1/me/referral-code)"
```

---

### Task 5: 온보딩 추천 보상 지급

**Files:**
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/request/UpdateUserDetailRequest.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/UserOnboardingController.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/in/CompleteOnboardingUseCase.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/CompleteOnboardingService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/ReferralRewardE2ETest.kt`

**Interfaces:**
- Consumes: Task 1 상수, Task 2 `User.canRefer`/`referredBy`, Task 3 `GetUserPort.findByReferralCode`
- Produces: `CompleteOnboardingUseCase.complete(userId: Long, command: UpdateUserDetailCommand, referralCode: String? = null)`; 요청 바디 선택 필드 `referralCode`

주의: `referralCode`는 프로필 필드가 아니므로 `UpdateUserDetailCommand`에 넣지 않고 별도 파라미터로 전달한다 (프로필 수정 경로 `UpdateProfileService` 등에 영향 없음).

- [ ] **Step 1: 실패하는 E2E 테스트 작성**

`ReferralRewardE2ETest.kt` 생성. `fullProfileBody`는 `CompleteOnboardingE2ETest.kt`의 private 함수라 재사용 불가 — 이 파일에 referralCode 포함 바디 빌더를 둔다:

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe

/**
 * 온보딩 완료 시 추천 코드 보상 E2E 테스트.
 * 유효한 코드면 추천인·신규 유저 모두 50코인, 무효 코드면 온보딩만 성공하고 지급이 없는지 검증한다.
 * (가입 축하 100코인은 항상 지급되므로 신규 유저 잔액은 100 또는 150이 된다)
 */
class ReferralRewardE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/complete + referralCode") {

		context("유효한 추천 코드로 온보딩을 완료하면") {
			it("추천인·신규 유저 모두 50코인을 받고 추천인이 기록된다 (200)") {
				val referrer = UserEntityFixture.create(status = UserStatus.ACTIVE)
				referrer.referralCode = "REFER123"
				val referrerId: Long = IntegrationUtil.persist(referrer).id!!

				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = "REFER123"))
				} expect {
					status(200)
					body("success", true)
				}

				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				coinBalanceOf(userId) shouldBe 150 // 가입 축하 100 + 추천 보상 50
				coinBalanceOf(referrerId) shouldBe 50
				referredByOf(userId) shouldBe referrerId
			}
		}

		context("존재하지 않는 추천 코드로 온보딩을 완료하면") {
			it("온보딩은 성공하고 추천 보상만 지급되지 않는다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = "NOSUCH00"))
				} expect {
					status(200)
					body("success", true)
				}

				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				coinBalanceOf(userId) shouldBe 100 // 가입 축하만
				referredByOf(userId) shouldBe null
			}
		}

		context("추천 코드 없이 온보딩을 완료하면") {
			it("기존과 동일하게 가입 축하 코인만 지급된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = null))
				} expect {
					status(200)
					body("success", true)
				}

				coinBalanceOf(userId) shouldBe 100
				referredByOf(userId) shouldBe null
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})

/** referralCode를 선택 포함하는 온보딩 완료 바디. (다른 필드는 CompleteOnboardingE2ETest의 fullProfileBody와 동일 값) */
private fun profileBodyWithReferral(regionId: Long, referralCode: String?): String {
	val referralJson: String = referralCode?.let { "\"$it\"" } ?: "null"
	return """
		{
		  "nickname": "테스트유저",
		  "birthday": "1995-01-01",
		  "height": 175,
		  "gender": "MALE",
		  "phoneNumber": "010-1234-5678",
		  "job": "개발자",
		  "regionId": $regionId,
		  "introduction": "안녕하세요 잘 부탁드립니다.",
		  "traits": ["성실함"],
		  "interests": ["영화"],
		  "maritalStatus": "SINGLE",
		  "smokingStatus": "NON_SMOKER",
		  "religion": "NONE",
		  "drinkingStatus": "SOMETIMES",
		  "bodyType": "MALE_NORMAL",
		  "referralCode": $referralJson
		}
	""".trimIndent()
}
```

`OnboardingE2ESupport.kt`에 헬퍼 추가:

```kotlin
/** 해당 사용자의 추천인(referred_by_user_id) 기록값. */
internal fun referredByOf(userId: Long): Long? =
	IntegrationUtil.getQuery()
		.select(QUserEntity.userEntity.referredByUserId)
		.from(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetchOne()
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.api.user.ReferralRewardE2ETest'`
Expected: FAIL — 유효 코드 케이스에서 `coinBalanceOf(userId)`가 100 (referralCode 필드가 무시됨)

- [ ] **Step 3: 요청 DTO·컨트롤러에 referralCode 전달**

`UpdateUserDetailRequest.kt` 프로퍼티에 추가 (`bodyType` 아래):

```kotlin
	/** 추천 코드(선택). 온보딩 완료 시에만 의미가 있고 프로필 수정에서는 무시된다. */
	@field:Size(max = 8, message = "추천 코드는 8자 이하여야 합니다.")
	val referralCode: String? = null,
```

(`toCommand()`는 변경하지 않는다 — referralCode는 command에 포함되지 않는다.)

`UserOnboardingController.complete()` 수정:

```kotlin
		completeOnboardingUseCase.complete(user.id, request.toCommand(), request.referralCode)
```

- [ ] **Step 4: 유스케이스 시그니처·서비스 구현**

`CompleteOnboardingUseCase.kt`:

```kotlin
	fun complete(userId: Long, command: UpdateUserDetailCommand, referralCode: String? = null)
```

`CompleteOnboardingService.kt` — import 추가:

```kotlin
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
```

(이미 있음 — `CoinPolicy`·`CoinGetType`도 이미 임포트됨.)

`complete` 오버라이드 수정:

```kotlin
	@Transactional
	override fun complete(userId: Long, command: UpdateUserDetailCommand, referralCode: String?) {
		updateUserDetailUseCase.update(userId, command)
		// 온보딩 단계에서 코인 잔액 행을 미리 준비해, 이후 적립/차감이 항상 기존 행을 갱신하게 한다. (조회 경로는 쓰기를 하지 않음)
		createCoinBalanceUseCase.createIfAbsent(userId)

		// 아직 ONBOARDING이면 정식 가입(ACTIVE)으로 전환하고, 이번 호출로 막 완료됐는지 반환한다.
		val justOnboarded: Boolean = completeSignUpIfOnboarding(userId)

		if (justOnboarded) {
			// 변경된 프로필/가입 상태로 매칭 읽기 모델(match_user)을 동기로 적재한다. (아래 추천이 이 모델을 읽으므로 추천보다 먼저 끝나 있어야 한다)
			syncMatchUser(userId)
			// 가입 축하 코인 지급·첫 1:1 매칭 소개를 같은 트랜잭션에서 동기로 처리한다.
			acquireCoinUseCase.acquire(
				userId,
				AcquireCoinCommand(CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT, CoinGetType.SIGNUP),
			)
			// 추천 코드가 유효하면 추천인·신규 유저 양쪽에 추천 보상 코인을 지급한다. (무효 코드는 조용히 무시)
			applyReferralIfPresent(userId, referralCode)
			recommendMatchUseCase.recommend(userId)
			recommendTeamUseCase.recommend(userId)
		}
	}
```

private 메서드 추가 (`completeSignUpIfOnboarding` 아래):

```kotlin
	/**
	 * 추천 코드가 있으면 추천인을 찾아 신규 유저에 추천인을 기록하고, 양쪽에 추천 보상 코인을 지급한다.
	 * 코드가 없거나(빈 값 포함) 무효(미존재·비활성 추천인·본인)면 조용히 무시한다. (온보딩은 정상 진행)
	 */
	private fun applyReferralIfPresent(userId: Long, referralCode: String?) {
		val code: String = referralCode?.trim().takeUnless { it.isNullOrEmpty() } ?: return
		val referrer: User = getUserPort.findByReferralCode(code) ?: return
		if (!referrer.canRefer(userId)) return

		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")
		saveUserPort.save(user.referredBy(referrer.id))

		acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT, CoinGetType.REFERRAL),
		)
		acquireCoinUseCase.acquire(
			referrer.id,
			AcquireCoinCommand(CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT, CoinGetType.REFERRAL),
		)
	}
```

- [ ] **Step 5: 테스트 통과 + 기존 온보딩 회귀 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.api.user.ReferralRewardE2ETest' --tests 'com.org.oneulsogae.api.user.CompleteOnboardingE2ETest'`
Expected: 모두 PASS

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core oneulsogae-api
git commit -m "feat(user): 온보딩 추천 코드 입력 시 추천인·신규 유저에 50코인 지급"
```

---

### Task 6: 웹 온보딩 — 추천 코드 스텝

**리포지토리:** `/Users/inwookjung/IdeaProjects/oneulsogae-frontend`

**Files:**
- Modify: `src/domains/onboarding/data/datasources/local/OnboardingDataSource.ts` (draft에 `referralCode` 추가)
- Modify: `src/domains/onboarding/domain/entities/OnboardingSubmission.ts`
- Modify: `src/domains/onboarding/presentation/hooks/submissionMapper.ts`
- Modify: `src/domains/onboarding/presentation/components/OnboardingSteps.tsx`

**Interfaces:**
- Consumes: 백엔드 `POST /users/v1/onboarding/complete`의 선택 필드 `referralCode` (Task 5)
- Produces: 온보딩 마지막 선택 스텝 "추천 코드"

- [ ] **Step 1: draft 필드 추가**

`OnboardingDataSource.ts`의 `OnboardingDraft`에 추가 (`identityGender` 위):

```ts
  /** 추천 코드(선택). 미입력이면 "". */
  referralCode: string;
```

`EMPTY_DRAFT`에 추가:

```ts
  referralCode: "",
```

(persist `merge`가 `EMPTY_DRAFT` 스프레드로 옛 저장 draft에 기본값을 채우므로 추가 마이그레이션 불필요.)

- [ ] **Step 2: 제출 엔티티·매퍼 반영**

`OnboardingSubmission.ts`에 추가:

```ts
  /** 추천 코드(선택). 미입력이면 보내지 않습니다. */
  referralCode?: string;
```

`submissionMapper.ts`의 `draftToSubmission`을 수정 (UserProfile은 표시용이라 referralCode를 넣지 않고 draft에서 직접 붙인다):

```ts
/** 온보딩 draft를 그대로 백엔드 제출 입력으로 변환합니다. (정규화 포함) */
export const draftToSubmission = (draft: OnboardingDraft): OnboardingSubmission => ({
  ...toSubmission(buildProfile(draft)),
  referralCode: draft.referralCode.trim().toUpperCase() || undefined,
});
```

- [ ] **Step 3: 스텝 추가**

`OnboardingSteps.tsx`의 `buildOnboardingSteps` 반환 배열 마지막(lifestyle 객체 뒤)에 추가:

```tsx
    {
      key: "referral",
      title: "추천 코드가 있나요?",
      subtitle: "없으면 그냥 완료해도 돼요. 입력하면 두 분 모두 50코인을 드려요!",
      isValid: () => true,
      render: () => (
        <Field>
          <Input
            id={QUESTION_ID}
            autoFocus
            maxLength={8}
            placeholder="예: AB12CD34"
            value={draft.referralCode}
            onChange={(e) => setField("referralCode", e.target.value.toUpperCase())}
            className="h-12 text-base uppercase"
          />
          <FieldDescription>친구에게 받은 8자 코드를 입력해주세요. (선택)</FieldDescription>
        </Field>
      ),
    },
```

- [ ] **Step 4: 검증**

Run: `npm run lint && npm run build` (frontend 루트에서)
Expected: 에러 없음. 이후 온보딩 위저드 마지막에 추천 코드 스텝이 뜨는지 dev 서버로 확인 가능하면 확인.

- [ ] **Step 5: 커밋 (frontend repo)**

```bash
git add src/domains/onboarding
git commit -m "feat(onboarding): 온보딩 마지막에 추천 코드 선택 입력 스텝 추가"
```

---

### Task 7: 웹 마이탭 — 내 추천 코드 화면

**리포지토리:** `/Users/inwookjung/IdeaProjects/oneulsogae-frontend`

**Files:**
- Create: `src/domains/user/data/datasources/remote/ReferralCodeDataSource.ts`
- Create: `src/domains/user/presentation/hooks/useMyReferralCode.ts`
- Create: `src/domains/user/presentation/components/ReferralCodeCard.tsx`
- Create: `src/app/profile/referral/page.tsx`
- Modify: `src/domains/user/presentation/components/MyTab.tsx` (코인 섹션에 LinkRow)

**Interfaces:**
- Consumes: 백엔드 `GET /users/v1/me/referral-code` → `{ referralCode }` (Task 4)
- Produces: 라우트 `/profile/referral`

- [ ] **Step 1: 데이터소스**

`ReferralCodeDataSource.ts` (httpClient의 GET 반환 형태는 `CoinDataSource.ts` 등 기존 GET 데이터소스에서 봉투 해제 방식 확인 후 동일하게 — 아래는 봉투가 자동 해제되어 data가 반환되는 전제):

```ts
import { httpClient } from "@/core/infrastructure/http/HttpClient";

interface ReferralCodeResponse {
  referralCode: string;
}

/**
 * DataSource — 내 추천 코드 조회 (`GET /users/v1/me/referral-code`).
 * 코드가 아직 없으면 서버가 발급해 반환합니다(get-or-create, 멱등).
 */
export async function getMyReferralCode(): Promise<string> {
  const res = await httpClient.get<ReferralCodeResponse>(
    "/users/v1/me/referral-code",
    { label: "[getMyReferralCode] /users/v1/me/referral-code" },
  );
  return res.referralCode;
}
```

- [ ] **Step 2: 조회 훅**

`useMyReferralCode.ts`:

```ts
import { useQuery } from "@tanstack/react-query";
import { getMyReferralCode } from "@/domains/user/data/datasources/remote/ReferralCodeDataSource";

/** 내 추천 코드를 조회(없으면 서버가 발급)하는 훅. 코드가 바뀌지 않으므로 무기한 캐시합니다. */
export function useMyReferralCode() {
  return useQuery({
    queryKey: ["user", "referral-code"],
    queryFn: getMyReferralCode,
    staleTime: Infinity,
  });
}
```

- [ ] **Step 3: 카드 컴포넌트 + 페이지**

`ReferralCodeCard.tsx` (프로젝트 공용 컴포넌트 스타일 — `Button`, brand 토큰 — 은 MyTab.tsx 상단 import를 참고해 동일 소스에서 가져온다):

```tsx
"use client";

import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { Button } from "@/core/ui/components/button";
import { useMyReferralCode } from "@/domains/user/presentation/hooks/useMyReferralCode";

/** 내 추천 코드 표시 + 복사. 친구가 온보딩에서 입력하면 서로 50코인을 받는다는 안내를 함께 보여줍니다. */
export function ReferralCodeCard() {
  const { data: code, isPending, isError } = useMyReferralCode();
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    if (!code) return;
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (isPending) return <p className="p-6 text-sm text-brand-sub">불러오는 중…</p>;
  if (isError || !code)
    return <p className="p-6 text-sm text-brand-sub">추천 코드를 불러오지 못했어요. 잠시 후 다시 시도해주세요.</p>;

  return (
    <section className="mt-6 rounded-2xl border border-brand-line bg-card p-6 text-center">
      <h2 className="text-sm font-semibold text-brand-sub">내 추천 코드</h2>
      <p className="mt-3 text-3xl font-bold tracking-[0.3em] text-brand-ink">{code}</p>
      <p className="mt-3 text-sm text-brand-sub">
        친구가 가입할 때 이 코드를 입력하면
        <br />
        친구도 나도 <span className="font-semibold text-brand-ink">50코인</span>을 받아요!
      </p>
      <Button onClick={copy} className="mt-5 h-11 w-full rounded-xl font-semibold">
        {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
        {copied ? "복사됐어요" : "코드 복사하기"}
      </Button>
    </section>
  );
}
```

`src/app/profile/referral/page.tsx` (기존 `/profile/company` 페이지의 레이아웃/헤더 패턴을 확인해 동일하게 감싼다 — 최소형):

```tsx
import { ReferralCodeCard } from "@/domains/user/presentation/components/ReferralCodeCard";

export default function ReferralPage() {
  return (
    <main className="mx-auto max-w-md px-4 pb-10">
      <ReferralCodeCard />
    </main>
  );
}
```

- [ ] **Step 4: 마이탭 진입점**

`MyTab.tsx` 코인 섹션에 LinkRow 추가 (lucide `Gift` 아이콘 import 추가):

```tsx
      {/* 코인 — 소비/획득 이력 페이지로 이동합니다. */}
      <SectionCard title="코인">
        <LinkRow
          icon={Coins}
          label="코인 내역"
          onClick={() => router.push("/coins/history")}
        />
        <LinkRow
          icon={Gift}
          label="내 추천 코드"
          onClick={() => router.push("/profile/referral")}
        />
      </SectionCard>
```

- [ ] **Step 5: 검증 + 커밋**

Run: `npm run lint && npm run build`
Expected: 에러 없음.

```bash
git add src/domains/user src/app/profile/referral
git commit -m "feat(my): 마이탭에 내 추천 코드 화면 추가 (조회·복사)"
```

---

### Task 8: 모바일 온보딩 — 추천 코드 스텝

**리포지토리:** `/Users/inwookjung/IdeaProjects/oneulsogae-mobile`

**Files:**
- Modify: `src/domains/onboarding/model/onboarding-draft.ts`
- Modify: `src/domains/onboarding/model/onboarding-steps.ts`
- Create: `src/domains/onboarding/ui/steps/referral-step.tsx`
- Modify: `src/domains/onboarding/ui/steps/index.ts`
- Modify: `src/domains/onboarding/api/onboarding-complete-api.ts`

**Interfaces:**
- Consumes: 백엔드 선택 필드 `referralCode` (Task 5)
- Produces: `STEP_KEYS` 마지막 `'referral'` 스텝

- [ ] **Step 1: draft 필드**

`onboarding-draft.ts`:

```ts
  religion: string; // Religion enum name
  /** 추천 코드(선택). 미입력이면 ''. */
  referralCode: string;
```

`emptyDraft`에 `referralCode: '',` 추가.

- [ ] **Step 2: 스텝 정의**

`onboarding-steps.ts`:

- `STEP_KEYS` 마지막에 `'referral',` 추가.
- `STEP_TITLES`에 `referral: '추천 코드가 있나요?',` 추가.
- `STEP_SUBTITLES`에 `referral: '없으면 그냥 완료해도 돼요. 입력하면 두 분 모두 50코인을 받아요',` 추가.
- `isStepValid` switch에 케이스 추가:

```ts
    case 'referral':
      return true;
```

- [ ] **Step 3: 스텝 컴포넌트**

`referral-step.tsx`:

```tsx
import { useOnboardingStore } from '@/domains/onboarding/model/onboarding-store';

import { TextField } from '../components/text-field';

export function ReferralStep() {
  const referralCode = useOnboardingStore((s) => s.draft.referralCode);
  const setField = useOnboardingStore((s) => s.setField);
  return (
    <TextField
      value={referralCode}
      onChangeText={(v) => setField('referralCode', v.toUpperCase())}
      placeholder="추천 코드 (예: AB12CD34)"
      maxLength={8}
    />
  );
}
```

`steps/index.ts`에 import·등록 추가:

```ts
import { ReferralStep } from './referral-step';
```

```ts
  referral: ReferralStep,
```

- [ ] **Step 4: 완료 요청 반영**

`onboarding-complete-api.ts`의 `CompleteOnboardingDto`에 추가:

```ts
  referralCode: string | null;
```

`buildCompleteRequest` 반환 객체에 추가:

```ts
    referralCode: draft.referralCode.trim().toUpperCase() || null,
```

- [ ] **Step 5: 검증 + 커밋**

Run: `npm run typecheck && npm run lint` (mobile 루트에서)
Expected: 에러 없음.

```bash
git add src/domains/onboarding
git commit -m "feat(onboarding): 온보딩 마지막에 추천 코드 선택 입력 스텝 추가"
```

---

### Task 9: 모바일 마이탭 — 내 추천 코드 화면

**리포지토리:** `/Users/inwookjung/IdeaProjects/oneulsogae-mobile`

**Files:**
- Create: `src/domains/user/api/referral-code-api.ts`
- Create: `src/domains/user/api/referral-code-queries.ts`
- Create: `src/domains/user/ui/referral-code-screen.tsx`
- Create: `src/app/referral-code.tsx`
- Modify: `src/domains/user/ui/my-screen.tsx` (코인 섹션에 LinkRow)
- Modify: `src/domains/user/index.ts` (도메인 배럴이 있으면 export 추가 — `src/app/coins/history.tsx`가 `@/domains/wallet`에서 화면을 가져오는 패턴 확인)

**Interfaces:**
- Consumes: `GET /users/v1/me/referral-code` (Task 4)
- Produces: 라우트 `/referral-code`

- [ ] **Step 1: API 함수**

`referral-code-api.ts`:

```ts
import { apiFetch } from '@/core/api/api-fetch';

// 백엔드 DTO — 이 레이어 밖으로 내보내지 않는다.
interface ReferralCodeDto {
  referralCode: string;
}
interface ApiEnvelope<T> {
  data: T;
}

/** 내 추천 코드 조회(없으면 서버가 발급, 멱등). GET /users/v1/me/referral-code */
export async function getMyReferralCode(): Promise<string> {
  const res = await apiFetch<ApiEnvelope<ReferralCodeDto>>('/users/v1/me/referral-code');
  return res.data.referralCode;
}
```

- [ ] **Step 2: 쿼리 훅**

`referral-code-queries.ts`:

```ts
import { useQuery } from '@tanstack/react-query';

import { getMyReferralCode } from './referral-code-api';

/** 내 추천 코드를 조회(없으면 서버가 발급)한다. 코드는 바뀌지 않으므로 무기한 캐시. */
export function useMyReferralCodeQuery() {
  return useQuery({
    queryKey: ['user', 'referral-code'],
    queryFn: getMyReferralCode,
    staleTime: Infinity,
  });
}
```

- [ ] **Step 3: 화면**

`referral-code-screen.tsx` (테마·컴포넌트 사용은 `my-screen.tsx`·`coin-purchase-screen.tsx` 스타일을 따른다. 공유는 RN 내장 `Share` — 추가 의존성 없음):

```tsx
import { ActivityIndicator, Pressable, Share, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Radius, Space } from '@/core/constants/theme';
import { useTheme } from '@/core/lib/use-theme';
import { ThemedText } from '@/core/ui/themed-text';

import { useMyReferralCodeQuery } from '../api/referral-code-queries';

/** 내 추천 코드 화면 — 코드 표시 + 공유. 친구가 온보딩에서 입력하면 서로 50코인. */
export function ReferralCodeScreen() {
  const theme = useTheme();
  const query = useMyReferralCodeQuery();

  const share = () => {
    if (!query.data) return;
    void Share.share({
      message: `오늘소개 가입할 때 내 추천 코드 ${query.data} 입력하면 우리 둘 다 50코인 받아요!`,
    });
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.brandCanvas }]}>
      {query.isPending ? (
        <ActivityIndicator color={theme.brandAccent} />
      ) : query.isError || !query.data ? (
        <ThemedText themeColor="brandSub">추천 코드를 불러오지 못했어요.</ThemedText>
      ) : (
        <View style={[styles.card, { borderColor: theme.brandLine, backgroundColor: theme.card }]}>
          <ThemedText type="smallBold" themeColor="brandSub">
            내 추천 코드
          </ThemedText>
          <ThemedText themeColor="brandInk" style={styles.code}>
            {query.data}
          </ThemedText>
          <ThemedText type="small" themeColor="brandSub" style={styles.hint}>
            친구가 가입할 때 이 코드를 입력하면{'\n'}친구도 나도 50코인을 받아요!
          </ThemedText>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="추천 코드 공유"
            onPress={share}
            style={[styles.button, { backgroundColor: theme.brandAccent }]}
          >
            <ThemedText type="smallBold" themeColor="onAccent">
              코드 공유하기
            </ThemedText>
          </Pressable>
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: Space.x2l },
  card: {
    alignSelf: 'stretch',
    alignItems: 'center',
    gap: Space.md,
    borderWidth: 1,
    borderRadius: Radius.card,
    padding: Space.x2l,
  },
  code: { fontSize: 32, lineHeight: 40, fontWeight: '700', letterSpacing: 8 },
  hint: { textAlign: 'center' },
  button: {
    alignSelf: 'stretch',
    minHeight: 52,
    borderRadius: Radius.control,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Space.md,
  },
});
```

주의: `Radius.card`가 없으면 `theme.ts`의 실제 키를 확인해 대체(`Radius.control` 등). 헤더 타이틀은 `_layout.tsx`의 다른 스택 화면(`company-verify` 등) 등록 패턴을 확인해 "내 추천 코드"로 동일하게 추가.

- [ ] **Step 4: 라우트 + 마이탭 진입점**

`src/app/referral-code.tsx`:

```tsx
export { ReferralCodeScreen as default } from '@/domains/user/ui/referral-code-screen';
```

(만약 `src/app/coins/history.tsx`처럼 도메인 배럴(`@/domains/user`) export 패턴이면 배럴에 `ReferralCodeScreen` export 추가 후 동일 형태로.)

`my-screen.tsx` 코인 섹션에 추가:

```tsx
        <SectionCard title="코인">
          <LinkRow
            icon={
              <MaterialCommunityIcons name="circle-multiple-outline" size={20} color={iconColor} />
            }
            label="코인 내역"
            onPress={() => router.push('/coins/history')}
          />
          <RowDivider />
          <LinkRow
            icon={<MaterialCommunityIcons name="gift-outline" size={20} color={iconColor} />}
            label="내 추천 코드"
            onPress={() => router.push('/referral-code')}
          />
        </SectionCard>
```

- [ ] **Step 5: 검증 + 커밋**

Run: `npm run typecheck && npm run lint`
Expected: 에러 없음.

```bash
git add src/domains/user src/app/referral-code.tsx
git commit -m "feat(my): 마이탭에 내 추천 코드 화면 추가 (조회·공유)"
```

---

## 최종 확인

- [ ] 백엔드 전체 테스트: `./gradlew test` — 회귀 없음 확인
- [ ] 운영 DDL 3줄(Global Constraints)을 사용자에게 보고
- [ ] 스펙 대비 누락 확인: 발급 API·온보딩 보상·무효 코드 무시·웹/모바일 온보딩 스텝·마이탭 화면
