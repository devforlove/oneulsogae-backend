# 가입 축하 100코인 지급 + 팝업 신호 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 신규 사용자가 회사 이메일 인증으로 온보딩을 완료하는 시점에 가입 축하 100코인을 1회 지급하고, 인증 확인 응답으로 프론트가 축하 팝업을 띄울 신호를 내려준다.

**Architecture:** 코인 지급은 사용자당 1회만 발행되는 `CompanyEmailVerified` 이벤트 리스너(`UserEventHandler.onCompanyEmailVerified`, AFTER_COMMIT/REQUIRES_NEW)에서 coin 도메인 in-port `AcquireCoinUseCase`를 호출해 처리한다. 팝업 신호는 인증 확인 유스케이스 결과(`VerifyCompanyEmailResult`)와 응답(`VerifyCompanyEmailResponse`)에 `justOnboarded`·`rewardCoin` 필드를 추가해 노출한다.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 4, Spring Data JPA, Kotest(E2E), RestAssuredDsl, Testcontainers, QueryDSL.

## Global Constraints

- 응답·주석·커밋 메시지는 한국어로 작성한다.
- `meeple-backend`만 수정한다. 프론트엔드 변경은 직접 하지 않고 안내만 한다.
- 도메인 간 참조는 in-port `UseCase`로만 한다(coin은 `AcquireCoinUseCase`).
- `LocalDateTime.now()` 직접 호출 금지(코인 지급의 시각은 기존 `AcquireCoinService`가 `TimeGenerator`로 처리하므로 신규 시각 호출 없음).
- 변수·반환·람다 파라미터 타입을 명시한다.
- 커밋 메시지 형식: `<type>(<domain>): <설명>`. 코인 도메인이 섞이는 변경은 변경의 주된 도메인 기준으로 표기.
- E2E는 `AbstractIntegrationSupport` 상속 + `IntegrationUtil`/엔티티 픽스처 + `RestAssuredDsl` 사용. 리포지토리 직접 의존 금지.

---

### Task 1: 코인 적립 유형·정책 상수 추가 (meeple-common)

가입 축하 코인을 출석/구매/환불과 구분하는 적립 유형과 지급 수량 상수를 추가한다.

**Files:**
- Modify: `meeple-common/src/main/kotlin/com/org/meeple/common/coin/CoinGetType.kt`
- Modify: `meeple-common/src/main/kotlin/com/org/meeple/common/coin/CoinPolicy.kt`

**Interfaces:**
- Produces:
  - `CoinGetType.SIGNUP` (enum 상수, `description = "가입 축하"`)
  - `CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT: Int = 100`

- [ ] **Step 1: `CoinGetType`에 SIGNUP 추가**

`REFUND` 항목 위(또는 아래)에 추가한다. 기존 항목 순서/주석은 건드리지 않는다.

```kotlin
	/** 무료로 획득한 코인. (출석/이벤트 등) */
	DAILY("출석 획득"),

	/** 결제로 구매한 코인. */
	PURCHASE("구매"),

	/** 소개팅 매칭 실패 등으로 사용한 코인의 일부를 되돌려준(환불) 코인. */
	REFUND("환불"),

	/** 신규 가입(온보딩 완료) 축하로 1회 지급하는 코인. */
	SIGNUP("가입 축하"),
```

- [ ] **Step 2: `CoinPolicy`에 지급 수량 상수 추가**

```kotlin
/** 코인 적립/사용 정책 상수. */
object CoinPolicy {

	/** 출석(DAILY) 보상으로 하루 1회 지급하는 코인 수량. */
	const val DAILY_REWARD_COIN_AMOUNT: Int = 1

	/** 신규 가입(온보딩 완료) 축하로 1회 지급하는 코인 수량. */
	const val SIGNUP_REWARD_COIN_AMOUNT: Int = 100
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :meeple-common:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add meeple-common/src/main/kotlin/com/org/meeple/common/coin/CoinGetType.kt \
        meeple-common/src/main/kotlin/com/org/meeple/common/coin/CoinPolicy.kt
git commit -m "feat(coin): 가입 축하 적립 유형(SIGNUP)·지급 수량 상수 추가"
```

---

### Task 2: 인증 확인 응답에 팝업 신호(justOnboarded, rewardCoin) 추가

회사 이메일 인증 확인 응답에 "이번 호출로 막 온보딩됐는지"와 "지급 코인 수량"을 실어 프론트 팝업 트리거 신호를 내려준다. (실제 코인 지급 배선은 Task 3)

`rewardCoin`은 서비스가 `justOnboarded`와 상수만으로 계산하므로 코인 지급 성공 여부와 무관하다.

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/port/in/result/VerifyCompanyEmailResult.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/VerifyCompanyEmailService.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/user/response/VerifyCompanyEmailResponse.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/user/ConfirmCompanyEmailVerificationE2ETest.kt`

**Interfaces:**
- Consumes: `CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT` (Task 1).
- Produces:
  - `VerifyCompanyEmailResult(companyName: String?, justOnboarded: Boolean, rewardCoin: Int)`
  - `VerifyCompanyEmailResponse(isCompanyResolved: Boolean, companyName: String?, justOnboarded: Boolean, rewardCoin: Int)`

- [ ] **Step 1: 실패하는 E2E 단언 추가**

`ConfirmCompanyEmailVerificationE2ETest.kt`의 두 온보딩 성공 컨텍스트에 응답 단언을 추가한다. (두 경로 모두 `justOnboarded=true` → `rewardCoin=100`)

"정식 가입(ACTIVE)되고 회사명이 확정된다" `expect` 블록에 추가:

```kotlin
				} expect {
					status(200)
					body("success", true)
					body("data.isCompanyResolved", true)
					body("data.companyName", "미플")
					body("data.justOnboarded", true)
					body("data.rewardCoin", 100)
				}
```

"회사명 미확정(COMPANY_NOT_RESOLVED) 상태가 된다" `expect` 블록에 추가:

```kotlin
				} expect {
					status(200)
					body("success", true)
					body("data.isCompanyResolved", false)
					body("data.companyName", nullValue())
					body("data.justOnboarded", true)
					body("data.rewardCoin", 100)
				}
```

- [ ] **Step 2: E2E를 실행해 실패를 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.user.ConfirmCompanyEmailVerificationE2ETest"`
Expected: FAIL (`data.justOnboarded` / `data.rewardCoin` 경로가 응답에 없어 단언 실패)

- [ ] **Step 3: `VerifyCompanyEmailResult`에 필드 추가**

```kotlin
package com.org.meeple.core.user.command.application.port.`in`.result

/**
 * 회사 이메일 인증 검증 결과.
 * 검증한 회사 이메일 도메인으로 회사명을 찾았는지([isCompanyResolved])와 찾은 회사명([companyName]),
 * 이번 호출로 온보딩이 막 완료됐는지([justOnboarded])와 그때 지급되는 가입 축하 코인 수량([rewardCoin])을 담는다.
 */
data class VerifyCompanyEmailResult(
	val companyName: String?,
	val justOnboarded: Boolean,
	val rewardCoin: Int,
) {

	/** 도메인 매핑으로 회사명을 찾았으면 true. (못 찾으면 false) */
	val isCompanyResolved: Boolean
		get() = companyName != null
}
```

- [ ] **Step 4: `VerifyCompanyEmailService.verify`가 새 필드를 채워 반환**

상단 import에 추가:

```kotlin
import com.org.meeple.common.coin.CoinPolicy
```

`verify` 마지막 `return`을 교체:

```kotlin
		// 온보딩이 막 완료됐다면 첫 매칭을 자동 소개한다. (UserEventHandler가 AFTER_COMMIT — match_user 동기화·커밋 이후 소개·코인 지급)
		if (justOnboarded) {
			domainEventPublisher.publish(CompanyEmailVerified(verification.userId))
		}

		// 막 온보딩됐다면 프론트가 가입 축하 팝업을 띄울 수 있도록 지급 코인 수량을 신호로 함께 내려준다.
		val rewardCoin: Int = if (justOnboarded) CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT else 0
		return VerifyCompanyEmailResult(companyName, justOnboarded, rewardCoin)
```

- [ ] **Step 5: `VerifyCompanyEmailResponse`에 필드 노출**

```kotlin
package com.org.meeple.api.user.response

import com.org.meeple.core.user.command.application.port.`in`.result.VerifyCompanyEmailResult

/**
 * 회사 이메일 인증번호 검증 결과 응답.
 * 회사명 매핑 성공 여부([isCompanyResolved])·찾은 회사명([companyName])과,
 * 이번 호출로 막 가입 완료됐는지([justOnboarded])·지급된 가입 축하 코인 수량([rewardCoin])을 내려준다.
 */
data class VerifyCompanyEmailResponse(
	val isCompanyResolved: Boolean,
	val companyName: String?,
	val justOnboarded: Boolean,
	val rewardCoin: Int,
) {
	companion object {
		fun of(result: VerifyCompanyEmailResult): VerifyCompanyEmailResponse =
			VerifyCompanyEmailResponse(
				isCompanyResolved = result.isCompanyResolved,
				companyName = result.companyName,
				justOnboarded = result.justOnboarded,
				rewardCoin = result.rewardCoin,
			)
	}
}
```

- [ ] **Step 6: `VerifyCompanyEmailResult` 생성자를 쓰는 다른 호출부 확인·수정**

Run: `grep -rn "VerifyCompanyEmailResult(" meeple-*/src --include=*.kt`
Expected: 위에서 고친 `VerifyCompanyEmailService`의 `return` 한 곳만 인스턴스를 생성한다. 다른 생성 호출부가 있으면 새 시그니처(`companyName, justOnboarded, rewardCoin`)에 맞춰 함께 수정한다. (테스트 픽스처 등)

- [ ] **Step 7: E2E를 실행해 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.user.ConfirmCompanyEmailVerificationE2ETest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/port/in/result/VerifyCompanyEmailResult.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/VerifyCompanyEmailService.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/user/response/VerifyCompanyEmailResponse.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/user/ConfirmCompanyEmailVerificationE2ETest.kt
git commit -m "feat(user): 회사 이메일 인증 응답에 가입 축하 팝업 신호(justOnboarded·rewardCoin) 추가"
```

---

### Task 3: 온보딩 완료 시 가입 축하 100코인 지급 (UserEventHandler)

`onCompanyEmailVerified` 리스너에서 coin in-port로 100코인을 추천보다 먼저 지급한다. E2E로 인증 후 잔액이 100인지, 회사명 직접입력 경로에서는 추가 지급이 없는지 검증한다.

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/UserEventHandler.kt`
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/user/OnboardingE2ESupport.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/user/ConfirmCompanyEmailVerificationE2ETest.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/user/ResolveCompanyNameE2ETest.kt`

**Interfaces:**
- Consumes:
  - `AcquireCoinUseCase.acquire(userId: Long, command: AcquireCoinCommand): CoinBalance` (coin in-port, 기존)
  - `AcquireCoinCommand(amount: Int, coinType: CoinGetType)` (기존)
  - `CoinGetType.SIGNUP`, `CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT` (Task 1)
- Produces:
  - `coinBalanceOf(userId: Long): Int` (E2E 헬퍼, `OnboardingE2ESupport.kt`)

- [ ] **Step 1: 잔액 값 조회 E2E 헬퍼 추가 + 코인 원장 정리 추가**

`OnboardingE2ESupport.kt` 상단 import에 `QCoinHistoryEntity` 추가:

```kotlin
import com.org.meeple.infra.coin.command.entity.QCoinHistoryEntity
```

`cleanupOnboarding()`의 `QCoinBalanceEntity` 정리 바로 아래에 코인 원장 정리를 추가한다(가입 축하 지급이 coins 원장에도 행을 남기므로 함께 격리):

```kotlin
	IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
```

파일 하단(기존 `coinBalanceCountOf` 아래)에 잔액 값 조회 헬퍼 추가:

```kotlin
/** 해당 사용자의 코인 잔액 값. 잔액 행이 없으면 0. (가입 축하 지급 검증용) */
internal fun coinBalanceOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.select(QCoinBalanceEntity.coinBalanceEntity.balance)
		.from(QCoinBalanceEntity.coinBalanceEntity)
		.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(userId))
		.fetchOne() ?: 0
```

- [ ] **Step 2: 실패하는 E2E 단언 추가 (잔액 100 검증)**

`ConfirmCompanyEmailVerificationE2ETest.kt`의 두 온보딩 성공 컨텍스트 끝(기존 `userStatusOf(...)` 단언 뒤)에 잔액 단언을 추가한다.

"정식 가입(ACTIVE)되고 회사명이 확정된다" 컨텍스트 끝:

```kotlin
				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				userDetailOf(userId).companyName shouldBe "미플"
				coinBalanceOf(userId) shouldBe 100
```

"회사명 미확정(COMPANY_NOT_RESOLVED) 상태가 된다" 컨텍스트 끝:

```kotlin
				userStatusOf(userId) shouldBe UserStatus.COMPANY_NOT_RESOLVED
				coinBalanceOf(userId) shouldBe 100
```

- [ ] **Step 3: E2E를 실행해 실패를 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.user.ConfirmCompanyEmailVerificationE2ETest"`
Expected: FAIL (코인 미지급으로 `coinBalanceOf(userId)`가 0 → 100 단언 실패)

- [ ] **Step 4: `UserEventHandler`에서 코인 지급 배선**

상단 import에 추가:

```kotlin
import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.common.coin.CoinPolicy
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
```

생성자에 의존성 추가:

```kotlin
@Component
class UserEventHandler(
	private val getUserPort: GetUserPort,
	private val getUserDetailPort: GetUserDetailPort,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val recommendMatchUseCase: RecommendMatchUseCase,
	private val recommendTeamUseCase: RecommendTeamUseCase,
	private val acquireCoinUseCase: AcquireCoinUseCase,
) {
```

`onCompanyEmailVerified` 본문에 코인 지급을 추천보다 먼저 추가하고, KDoc에 코인 지급을 반영한다:

```kotlin
	/**
	 * 회사 이메일 인증으로 온보딩이 막 완료됨 → 가입 축하 코인을 지급하고, 첫 1:1 매칭 소개와 첫 팀 추천 적재를 함께 처리한다. (CQS: 조회 경로가 아니라 인증 완료 시점에 처리)
	 * 매칭 읽기 모델(match_user)이 같은 트랜잭션의 BEFORE_COMMIT 동기화로 적재·커밋된 뒤라야 후보를 고를 수 있으므로
	 * 커밋 이후(AFTER_COMMIT)에 새 트랜잭션(REQUIRES_NEW)으로 처리한다. (지급·소개·적재 실패가 인증을 롤백시키지 않음 — best-effort)
	 * 중복 지급은 이 리스너가 사용자당 1회(justOnboarded)만 발행되는 것에 의존한다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onCompanyEmailVerified(event: CompanyEmailVerified) {
		acquireCoinUseCase.acquire(
			event.userId,
			AcquireCoinCommand(CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT, CoinGetType.SIGNUP),
		)
		recommendMatchUseCase.recommend(event.userId)
		recommendTeamUseCase.recommend(event.userId)
	}
```

- [ ] **Step 5: E2E를 실행해 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.user.ConfirmCompanyEmailVerificationE2ETest"`
Expected: PASS

- [ ] **Step 6: 회사명 직접입력 경로에 코인 추가 지급 없음 단언 추가**

`ResolveCompanyNameE2ETest.kt`의 성공 컨텍스트에서, 이미 가입 축하 코인을 받은 COMPANY_NOT_RESOLVED 사용자가 회사명을 직접 입력해도 잔액이 그대로(100)인지 확인한다. 해당 사용자 셋업에 기존 잔액 행을 추가하고, 호출 뒤 잔액 단언을 더한다.

필요 import 확인(없으면 추가): `import com.org.meeple.infra.fixture.CoinBalanceEntityFixture`.

성공 케이스의 사용자 셋업 직후(프로필/유저 픽스처 persist 부분)에 추가:

```kotlin
					IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = 100))
```

`post(...) expect { ... }` 블록 뒤(기존 상태 단언과 함께)에 추가:

```kotlin
					coinBalanceOf(userId) shouldBe 100
```

`io.kotest.matchers.shouldBe` import가 없으면 추가한다.

- [ ] **Step 7: 회사명 직접입력 E2E를 실행해 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.user.ResolveCompanyNameE2ETest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/UserEventHandler.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/user/OnboardingE2ESupport.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/user/ConfirmCompanyEmailVerificationE2ETest.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/user/ResolveCompanyNameE2ETest.kt
git commit -m "feat(coin): 온보딩 완료 시 가입 축하 100코인 지급"
```

---

### Task 4: 전체 빌드·테스트 검증

**Files:** 없음(검증 전용)

- [ ] **Step 1: 전체 빌드·테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (전 모듈 컴파일 + 유닛/E2E 통과)

- [ ] **Step 2: 프론트엔드 안내 출력**

`meeple-frontend` 담당에게 전달할 변경 안내를 사용자에게 출력한다(직접 수정하지 않는다):

- 대상 API: `POST /users/v1/onboarding/company-email/verifications/confirm`
- 응답 `data`에 필드 2개 추가됨: `justOnboarded: boolean`, `rewardCoin: number`
- 대응 DTO에 두 필드를 추가하고, 인증 성공 콜백에서 `justOnboarded === true && rewardCoin > 0`이면 가입 축하 팝업("축하합니다! {rewardCoin}코인을 받았어요")을 노출.

---

## Self-Review

**1. Spec coverage**
- 코인 적립 유형·정책(SIGNUP, 100) → Task 1 ✓
- 온보딩 완료 시 1회 지급(UserEventHandler) → Task 3 ✓
- 팝업 신호(justOnboarded, rewardCoin) 결과·응답 노출 → Task 2 ✓
- 중복 지급: 리스너 1회 보장 의존(추가 가드 없음) → Task 3에서 가드 미추가 ✓
- 프론트 안내(직접 수정 안 함) → Task 4 Step 2 ✓
- E2E: 응답 신호·잔액 100·resolve 경로 추가 지급 없음 → Task 2/Task 3 ✓

**2. Placeholder scan:** 모든 코드 스텝에 실제 코드/명령/기대 출력 포함. 플레이스홀더 없음.

**3. Type consistency:**
- `VerifyCompanyEmailResult(companyName, justOnboarded, rewardCoin)` — Task 2에서 정의, 동일 시그니처로 일관.
- `VerifyCompanyEmailResponse(isCompanyResolved, companyName, justOnboarded, rewardCoin)` — Task 2 일관.
- `CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT`·`CoinGetType.SIGNUP` — Task 1 정의, Task 2/3에서 동일 이름 사용.
- `AcquireCoinCommand(amount, coinType)`·`AcquireCoinUseCase.acquire(userId, command)` — 기존 시그니처와 일치.
- `coinBalanceOf(userId)` — Task 3 Step 1 정의, 같은 패키지 테스트에서 사용.
