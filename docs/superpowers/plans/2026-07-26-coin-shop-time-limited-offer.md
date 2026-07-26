# 코인샵 기간 한정 오퍼(가입 후 N일) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코인샵에 유저 가입시각 기준 상대 유효기간(가입 후 N일)으로 만료되는 기간 한정 상품(예: 1회 한정 2배)을 추가한다.

**Architecture:** `coin_items.valid_days` 컬럼 1개만 두고 만료는 `가입시각 + valid_days` 로 계산(유저별 저장 없음). "2배"는 coin_amount 2배인 별도 PG·once_per_user 상품(데이터). 게이트 2겹 — 상점 목록에서 만료 오퍼 숨김(주력) + 체크아웃에서 만료 재검증(PG 캡처 전, 과금 없음)해 `COIN-005` 400. 만료 판정은 도메인 술어(`CoinItem.isOfferActiveAt`/`CoinItems.activeOffersAt`), 가입시각은 `validDays != null` 상품이 있을 때만 지연 조회.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6, Spring Data JPA + QueryDSL, MySQL. 헥사고날 + CQRS 멀티모듈(common·core·infra·api). Kotest 유닛(도메인) + Testcontainers E2E(api).

## Global Constraints

- 응답·주석·커밋 메시지는 한국어. 식별자는 영어. 타입 명시(변수·반환·람다 파라미터).
- `oneulsogae-backend`만 수정(프론트/모바일 금지).
- 현재 시각은 `com.org.oneulsogae.core.common.time.TimeGenerator.now()` 주입해 사용(직접 `LocalDateTime.now()` 금지). 도메인 메서드엔 `now`·`userCreatedAt` 를 파라미터로 전달.
- 타 도메인 데이터는 in-port로만(coin→user는 `GetUserByIdUseCase`).
- CQRS: 조회 서비스 `@Transactional(readOnly = true)`. 도메인 규칙은 서비스에 인라인 금지 — 도메인 모델/일급 컬렉션 메서드로 캡슐화.
- 만료 경계는 exclusive: 유효 조건 = `now < userCreatedAt + validDays일`. `validDays == null` = 상시(항상 유효).
- 가입시각 조회는 기간 한정 상품(`validDays != null`)이 있을 때만(lazy). 상시 상품만이면 user 조회 생략 — 미가입 userId 흐름·기존 테스트 보존.
- 채널 PG 전용(IAP 이벤트 상품 비목표). 에러코드 신규 `COIN-005`.
- 기존 코인 상점·체크아웃·구매 E2E 회귀 통과 필수.

---

### Task 1: `CoinItem` 읽기 모델 — validDays 필드·create 파라미터·만료 판정 술어

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dto/CoinItem.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinItemTest.kt`

**Interfaces:**
- Consumes: 없음(도메인 dto).
- Produces:
  - `CoinItem(id, coinAmount, price, salePrice, oncePerUser, saleChannel, storeProductId, validDays: Int? = null)` — 8 필드.
  - `CoinItem.create(coinAmount, price, salePrice, oncePerUser, saleChannel, storeProductId, validDays: Int? = null): CoinItem`.
  - `CoinItem.isOfferActiveAt(userCreatedAt: LocalDateTime, now: LocalDateTime): Boolean`.

- [ ] **Step 1: 실패 테스트 작성** — `CoinItemTest.kt` 의 최상위 `DescribeSpec({ ... })` 블록 안, 기존 `describe("create") { ... }` 블록 **뒤**에 아래 describe를 추가한다. (파일 상단 import에 `import java.time.LocalDateTime` 추가)

```kotlin
	describe("isOfferActiveAt") {
		val createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

		it("validDays가 null이면 언제나 활성이다(상시 상품)") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 100, price = 12000, salePrice = 10000, validDays = null,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2999, 1, 1, 0, 0)) shouldBe true
		}

		it("가입시각 + validDays일 직전이면 활성이다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 200, price = 10000, salePrice = 4900, oncePerUser = true, validDays = 7,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2026, 1, 7, 23, 59)) shouldBe true
		}

		it("가입시각 + validDays일 정각이면 만료다(exclusive 경계)") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 200, price = 10000, salePrice = 4900, oncePerUser = true, validDays = 7,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2026, 1, 8, 0, 0)) shouldBe false
		}

		it("가입시각 + validDays일 이후면 만료다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 200, price = 10000, salePrice = 4900, oncePerUser = true, validDays = 7,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2026, 1, 9, 0, 0)) shouldBe false
		}
	}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `isOfferActiveAt` 미해결, `create(... validDays = ...)` 미해결.

- [ ] **Step 3: 구현** — `CoinItem.kt` 를 아래로 수정한다. (① data class에 `validDays: Int? = null` 필드 추가 ② `create` 팩토리에 `validDays` 파라미터 추가 및 생성자 전달 ③ `isOfferActiveAt` 메서드 추가 ④ 상단에 `import java.time.LocalDateTime` 추가)

data class 필드에 마지막으로 추가:
```kotlin
data class CoinItem(
	val id: Long = 0,
	val coinAmount: Int,
	val price: Int,
	val salePrice: Int,
	val oncePerUser: Boolean = false,
	val saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
	val storeProductId: String? = null,
	/** 유저 가입시각 기준 유효일수. null이면 상시 판매. N이면 가입시각 + N일까지만 노출·구매 가능. */
	val validDays: Int? = null,
) {
```

`isOfferActiveAt` 메서드를 `discountRate` getter 아래, `companion object` 위에 추가:
```kotlin
	/**
	 * 이 상품이 [now] 시점에 이 유저에게 판매 활성인지 여부.
	 * [validDays]가 null이면 상시(항상 true). N이면 가입시각([userCreatedAt]) + N일 직전까지 활성이다(만료 시각 exclusive).
	 */
	fun isOfferActiveAt(userCreatedAt: LocalDateTime, now: LocalDateTime): Boolean =
		validDays?.let { now.isBefore(userCreatedAt.plusDays(it.toLong())) } ?: true
```

`create` 팩토리 시그니처·본문 수정(파라미터·생성자에 `validDays` 추가):
```kotlin
		fun create(
			coinAmount: Int,
			price: Int,
			salePrice: Int,
			oncePerUser: Boolean = false,
			saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
			storeProductId: String? = null,
			validDays: Int? = null,
		): CoinItem {
			require(coinAmount > 0) { "코인 개수는 1 이상이어야 합니다." }
			require(price > 0) { "정가는 1 이상이어야 합니다." }
			require(salePrice > 0) { "할인가는 1 이상이어야 합니다." }
			require(salePrice <= price) { "할인가는 정가보다 클 수 없습니다." }
			if (saleChannel.sellableVia(CoinSaleChannel.IAP)) {
				require(!storeProductId.isNullOrBlank()) { "IAP 판매 상품은 스토어 상품 id가 필요합니다." }
			}
			return CoinItem(
				coinAmount = coinAmount,
				price = price,
				salePrice = salePrice,
				oncePerUser = oncePerUser,
				saleChannel = saleChannel,
				storeProductId = storeProductId,
				validDays = validDays,
			)
		}
```

상단 import에 추가:
```kotlin
import java.time.LocalDateTime
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.coin.CoinItemTest"`
Expected: PASS (기존 create 테스트 + 신규 isOfferActiveAt 4건).

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dto/CoinItem.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinItemTest.kt
git commit -m "feat(coin): CoinItem에 validDays·만료 판정(isOfferActiveAt) 추가"
```

---

### Task 2: `CoinItems` 일급 컬렉션 — 만료 필터·기간한정 보유 판정

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dto/CoinItems.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinItemsTest.kt` (신규)

**Interfaces:**
- Consumes: `CoinItem`·`CoinItem.isOfferActiveAt`·`CoinItem.validDays` (Task 1).
- Produces:
  - `CoinItems.hasTimeLimitedOffer(): Boolean`
  - `CoinItems.activeOffersAt(userCreatedAt: LocalDateTime, now: LocalDateTime): CoinItems`

- [ ] **Step 1: 실패 테스트 작성** — 신규 파일 `CoinItemsTest.kt`:

```kotlin
package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [CoinItems] 일급 컬렉션 유닛 테스트.
 * 기간 한정 상품 보유 판정과, 특정 시점 만료 오퍼 제거를 검증한다.
 */
class CoinItemsTest : DescribeSpec({

	val createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
	val now: LocalDateTime = LocalDateTime.of(2026, 1, 5, 0, 0)

	fun item(id: Long, validDays: Int?): CoinItem =
		CoinItem(id = id, coinAmount = 100, price = 12000, salePrice = 10000, validDays = validDays)

	describe("hasTimeLimitedOffer") {
		it("validDays가 있는 상품이 하나라도 있으면 true다") {
			CoinItems(listOf(item(1, null), item(2, 7))).hasTimeLimitedOffer() shouldBe true
		}
		it("전부 상시(validDays null)면 false다") {
			CoinItems(listOf(item(1, null), item(2, null))).hasTimeLimitedOffer() shouldBe false
		}
	}

	describe("activeOffersAt") {
		it("만료된 기간 한정 상품은 제거하고 상시·유효 상품은 남긴다") {
			// id=1 상시(유지), id=2 유효(가입+7일, now=1/5 이전 → 유지), id=3 만료(가입+2일, now=1/5 이후 → 제거)
			val items = CoinItems(listOf(item(1, null), item(2, 7), item(3, 2)))

			val result: CoinItems = items.activeOffersAt(createdAt, now)

			result.values.map { it.id } shouldBe listOf(1L, 2L)
		}
	}
})
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `hasTimeLimitedOffer`·`activeOffersAt` 미해결.

- [ ] **Step 3: 구현** — `CoinItems.kt` 에 두 메서드를 추가한다(`isEmpty()` 아래, `companion object` 위). 상단에 `import java.time.LocalDateTime` 추가.

```kotlin
	/** 기간 한정 상품(validDays != null)을 하나라도 포함하는지 여부. */
	fun hasTimeLimitedOffer(): Boolean = values.any { it.validDays != null }

	/** [now] 시점 기준 만료된 기간 한정 오퍼를 제거한 새 목록을 반환한다. (상시 상품은 유지) */
	fun activeOffersAt(userCreatedAt: LocalDateTime, now: LocalDateTime): CoinItems =
		CoinItems(values.filter { it.isOfferActiveAt(userCreatedAt, now) })
```

`CoinItems.kt` 상단 import:
```kotlin
import java.time.LocalDateTime
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.coin.CoinItemsTest"`
Expected: PASS (3건).

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dto/CoinItems.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinItemsTest.kt
git commit -m "feat(coin): CoinItems에 기간 한정 오퍼 필터(activeOffersAt) 추가"
```

---

### Task 3: `CoinErrorCode` — 만료 오퍼 에러코드

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/CoinErrorCode.kt`

**Interfaces:**
- Produces: `CoinErrorCode.COIN_ITEM_OFFER_EXPIRED`("COIN-005", 400).

- [ ] **Step 1: 구현** — 기존 enum 상수 목록의 `COIN_ITEM_NOT_FOUND` 아래에 한 줄 추가:

```kotlin
	INSUFFICIENT_COIN_BALANCE("COIN-001", "코인 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
	DAILY_COIN_ALREADY_ACQUIRED("COIN-003", "오늘은 이미 출석 코인을 받았습니다.", HttpStatus.CONFLICT),
	COIN_ITEM_NOT_FOUND("COIN-004", "코인 상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	COIN_ITEM_OFFER_EXPIRED("COIN-005", "판매 기간이 종료된 상품입니다.", HttpStatus.BAD_REQUEST),
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/CoinErrorCode.kt
git commit -m "feat(coin): 기간 만료 상품 에러코드 COIN-005 추가"
```

---

### Task 4: `UserView` — 가입시각(createdAt) 노출

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/dto/UserView.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetUserDaoImpl.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetUserWithDetailDaoImpl.kt`

**Interfaces:**
- Produces: `UserView(id, email, status, createdAt: LocalDateTime)` — `createdAt` 는 가입시각(UserEntity.created_at).

- [ ] **Step 1: `UserView` 필드 추가** — `UserView.kt`:

```kotlin
package com.org.oneulsogae.core.user.query.dto

import com.org.oneulsogae.common.user.UserStatus
import java.time.LocalDateTime

/**
 * 사용자 계정 조회 결과(read model). query는 command 도메인([com.org.oneulsogae.core.user.command.domain.User]) 대신 이 view를 쓴다.
 */
data class UserView(
	val id: Long,
	val email: String?,
	val status: UserStatus,
	val createdAt: LocalDateTime,
) {

	/** 정식 가입(ACTIVE 등) 상태인지 여부. */
	val isRegistered: Boolean
		get() = status.isRegistered()
}
```

- [ ] **Step 2: `GetUserDaoImpl` 투영 확장** — `Projections.constructor(UserView::class.java, ...)` 에 `user.createdAt` 추가:

```kotlin
			.select(
				Projections.constructor(
					UserView::class.java,
					user.id,
					user.email,
					user.status,
					user.createdAt,
				),
			)
```

- [ ] **Step 3: `GetUserWithDetailDaoImpl` 투영 확장** — 중첩 `Projections.constructor(UserView::class.java, user.id, user.email, user.status)` 를 `user.createdAt` 추가로 수정:

```kotlin
					Projections.constructor(
						UserView::class.java,
						user.id,
						user.email,
						user.status,
						user.createdAt,
					),
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/dto/UserView.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetUserDaoImpl.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetUserWithDetailDaoImpl.kt
git commit -m "feat(user): UserView에 가입시각(createdAt) 노출"
```

---

### Task 5: 영속성 — `CoinItemEntity`·`GetCoinItemDaoImpl` 투영·픽스처에 validDays

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/command/entity/CoinItemEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/query/GetCoinItemDaoImpl.kt`
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/CoinItemEntityFixture.kt`

**Interfaces:**
- Consumes: `CoinItem`(8-arg, Task 1).
- Produces: `CoinItemEntity(..., validDays: Int? = null)` (컬럼 `valid_days`), `CoinItemEntityFixture.create(..., validDays: Int? = null)`, `GetCoinItemDaoImpl` 8-arg 투영.

- [ ] **Step 1: `CoinItemEntity` 컬럼 추가** — `storeProductId` 필드 아래(생성자 마지막)에 추가:

```kotlin
	/** 스토어 상품 id(SKU). IAP 검증이 SKU→coin_item 해석에 쓴다. PG 전용 상품은 null. 유니크. */
	@Column(name = "store_product_id", unique = true)
	var storeProductId: String? = null,

	/** 유저 가입시각 기준 유효일수. null이면 상시 판매. */
	@Column(name = "valid_days")
	var validDays: Int? = null,
) : BaseEntity()
```

- [ ] **Step 2: `GetCoinItemDaoImpl` 투영 8-arg 확장** — `projection(coinItem)` 함수의 `Projections.constructor` 마지막에 `coinItem.validDays` 추가하고 주석 갱신:

```kotlin
	/** 8-arg CoinItem 생성자 투영. (id, coinAmount, price, salePrice, oncePerUser, saleChannel, storeProductId, validDays) */
	private fun projection(coinItem: QCoinItemEntity) =
		Projections.constructor(
			CoinItem::class.java,
			coinItem.id,
			coinItem.coinAmount,
			coinItem.price,
			coinItem.salePrice,
			coinItem.oncePerUser,
			coinItem.saleChannel,
			coinItem.storeProductId,
			coinItem.validDays,
		)
```

- [ ] **Step 3: `CoinItemEntityFixture` 파라미터 추가** — `create(...)` 에 `validDays: Int? = null` 파라미터·생성자 전달 추가:

```kotlin
	fun create(
		coinAmount: Int = 100,
		price: Int = 12000,
		salePrice: Int = 10000,
		oncePerUser: Boolean = false,
		saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
		storeProductId: String? = null,
		validDays: Int? = null,
	): CoinItemEntity =
		CoinItemEntity(
			coinAmount = coinAmount,
			price = price,
			salePrice = salePrice,
			oncePerUser = oncePerUser,
			saleChannel = saleChannel,
			storeProductId = storeProductId,
			validDays = validDays,
		)
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-infra:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL (kapt가 QCoinItemEntity에 validDays 재생성).

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/command/entity/CoinItemEntity.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/query/GetCoinItemDaoImpl.kt \
        oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/CoinItemEntityFixture.kt
git commit -m "feat(coin): coin_items valid_days 컬럼·투영·픽스처 추가"
```

---

### Task 6: `GetCoinShopService` — 만료 오퍼 노출 게이트(지연 가입시각 조회)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/service/GetCoinShopService.kt`

**Interfaces:**
- Consumes: `CoinItems.hasTimeLimitedOffer`·`activeOffersAt`(Task 2), `GetUserByIdUseCase.getById(id): UserView`·`UserView.createdAt`(Task 4), `TimeGenerator.now()`(기존 core `common/time`).
- Produces: `GetCoinShopService.getCoinShop(userId, channel): CoinItems` (만료 오퍼 제외).

- [ ] **Step 1: 구현** — `GetCoinShopService.kt` 전체를 아래로 교체:

```kotlin
package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinShopUseCase
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetCoinShopUseCase] 구현.
 * 요청 채널(+BOTH) 상품 중 이미 구매한 1회 패키지를 제외하고, 기간 한정 상품은 만료된 것을 걸러 반환한다.
 * 만료 판정에 필요한 유저 가입시각은 기간 한정 상품이 실제로 있을 때만 조회한다(상시 상품만이면 유저 조회 생략).
 */
@Service
@Transactional(readOnly = true)
class GetCoinShopService(
	private val getCoinItemDao: GetCoinItemDao,
	private val getUserByIdUseCase: GetUserByIdUseCase,
	private val timeGenerator: TimeGenerator,
) : GetCoinShopUseCase {

	override fun getCoinShop(userId: Long, channel: CoinSaleChannel): CoinItems {
		val items: CoinItems = getCoinItemDao.findShopItems(userId, channel)
		if (!items.hasTimeLimitedOffer()) {
			return items
		}
		val now: LocalDateTime = timeGenerator.now()
		val userCreatedAt: LocalDateTime = getUserByIdUseCase.getById(userId).createdAt
		return items.activeOffersAt(userCreatedAt, now)
	}
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/service/GetCoinShopService.kt
git commit -m "feat(coin): 상점 목록에서 만료된 기간 한정 오퍼 제외"
```

---

### Task 7: `GetCoinCheckout` — 결제-전 만료 게이트(userId 추가) + 호출자 갱신

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/service/port/in/GetCoinCheckoutUseCase.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/service/GetCoinCheckoutService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/CompleteCoinPurchaseService.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/PaymentsController.kt`

**Interfaces:**
- Consumes: `CoinItem.validDays`·`isOfferActiveAt`(Task 1), `CoinErrorCode.COIN_ITEM_OFFER_EXPIRED`(Task 3), `GetUserByIdUseCase`·`UserView.createdAt`(Task 4), `TimeGenerator.now()`, `GetCoinItemDao.findById`(기존).
- Produces: `GetCoinCheckoutUseCase.getCheckout(userId: Long, itemId: Long): CoinItem`.

- [ ] **Step 1: in-port 시그니처 변경** — `GetCoinCheckoutUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.service.port.`in`

import com.org.oneulsogae.core.coin.query.dto.CoinItem

/** 코인 구매 체크아웃 화면에 노출할 코인 아이템을 조회하는 인포트(유스케이스). */
interface GetCoinCheckoutUseCase {

	fun getCheckout(userId: Long, itemId: Long): CoinItem
}
```

- [ ] **Step 2: 서비스 구현** — `GetCoinCheckoutService.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.core.coin.CoinErrorCode
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinCheckoutUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetCoinCheckoutUseCase] 구현.
 * 코인 구매 체크아웃에 필요한 코인 아이템을 조회한다. 없으면 [CoinErrorCode.COIN_ITEM_NOT_FOUND].
 * 기간 한정 상품(validDays != null)이면 유저 가입시각 기준 만료를 재검증해, 만료면 [CoinErrorCode.COIN_ITEM_OFFER_EXPIRED].
 * 이 검증은 PG 최종 승인(capture) 전에 수행돼 만료 상품의 헛된 과금을 막는다(상점 목록 캐시 스테일·직접 호출 대비 결제-전 게이트).
 */
@Service
@Transactional(readOnly = true)
class GetCoinCheckoutService(
	private val getCoinItemDao: GetCoinItemDao,
	private val getUserByIdUseCase: GetUserByIdUseCase,
	private val timeGenerator: TimeGenerator,
) : GetCoinCheckoutUseCase {

	override fun getCheckout(userId: Long, itemId: Long): CoinItem {
		val item: CoinItem = getCoinItemDao.findById(itemId)
			?: throw BusinessException(CoinErrorCode.COIN_ITEM_NOT_FOUND)
		if (item.validDays == null) {
			return item
		}
		val now: LocalDateTime = timeGenerator.now()
		val userCreatedAt: LocalDateTime = getUserByIdUseCase.getById(userId).createdAt
		if (!item.isOfferActiveAt(userCreatedAt, now)) {
			throw BusinessException(CoinErrorCode.COIN_ITEM_OFFER_EXPIRED)
		}
		return item
	}
}
```

- [ ] **Step 3: `CompleteCoinPurchaseService` 호출 갱신** — `getCoinCheckoutUseCase.getCheckout(command.itemId)` 를 `getCoinCheckoutUseCase.getCheckout(userId, command.itemId)` 로 수정한다(해당 한 줄만):

```kotlin
		// 코인 상품 조회(없으면 COIN-004). 기간 한정이면 가입시각 기준 만료 재검증(만료면 COIN-005). salePrice가 서버 확정 실결제가.
		val item: CoinItem = getCoinCheckoutUseCase.getCheckout(userId, command.itemId)
```

- [ ] **Step 4: `PaymentsController` 호출 갱신** — `getCoinCheckout` 핸들러의 `getCoinCheckoutUseCase.getCheckout(itemId)` 를 `getCoinCheckoutUseCase.getCheckout(user.id, itemId)` 로 수정. Swagger 설명에 만료 코드도 보강:

```kotlin
	/** 코인 구매 직전 체크아웃 데이터(구매할 코인 아이템 + 구매방법)를 조회한다. */
	@Operation(
		summary = "코인 체크아웃 조회",
		description = "구매할 코인 아이템(itemId)과 활성 구매방법(결제수단) 목록을 반환한다. 코인 상품 없음 404(COIN-004), 판매 기간 종료 400(COIN-005).",
	)
	@GetMapping("/coin/checkout")
	fun getCoinCheckout(
		@LoginUser user: AuthUser,
		@RequestParam itemId: Long,
	): ApiResponse<CoinCheckoutResponse> =
		ApiResponse.success(
			CoinCheckoutResponse.of(
				user.id,
				getCoinCheckoutUseCase.getCheckout(user.id, itemId),
				getPaymentMethodsUseCase.getActiveMethods(),
			),
		)
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/service/port/in/GetCoinCheckoutUseCase.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/service/GetCoinCheckoutService.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/CompleteCoinPurchaseService.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/PaymentsController.kt
git commit -m "feat(coin): 체크아웃에서 만료 기간 한정 상품 결제-전 차단(COIN-005)"
```

---

### Task 8: E2E + 마이그레이션·시드 + 전체 테스트

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/coin/CoinTimeLimitedOfferE2ETest.kt`
- Create: `docs/migration/coin_items_valid_days.sql`
- Create: `docs/migration/coin_item_seed_double_welcome.sql`

**Interfaces:**
- Consumes: `GET /coins/v1/shop`, `GET /payments/v1/coin/checkout`, `CoinItemEntityFixture.create(validDays=...)`(Task 5), `UserEntityFixture`, `IntegrationUtil`, `AbstractIntegrationSupport`.

**만료 유저 시뮬레이션 방법:** E2E는 실시각(SystemTimeGenerator)을 쓰고 `UserEntity.created_at` 은 persist 시각으로 자동 설정된다(픽스처로 과거 지정 불가). 따라서 **활성 = `validDays = 30`**(방금 가입 → `now < created_at + 30일`), **만료 = `validDays = 0`**(`now < created_at + 0일` = `now < created_at` → persist 직후 항상 false → 만료)로 결정적으로 재현한다. 경계 정밀 검증은 Task 1·2 유닛이 담당한다.

- [ ] **Step 1: E2E 작성** — `CoinTimeLimitedOfferE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.coin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not

/**
 * 코인샵 기간 한정 오퍼 E2E 테스트.
 * 만료(validDays=0)·활성(validDays=30) 오퍼가 상점 목록 노출과 체크아웃 결제-전 게이트에서 어떻게 처리되는지 검증한다.
 * (실시각 기준: 방금 가입한 유저는 validDays=30이면 활성, validDays=0이면 즉시 만료)
 */
class CoinTimeLimitedOfferE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}

	describe("GET /coins/v1/shop (기간 한정 오퍼)") {

		context("만료된 기간 한정 상품과 상시 상품이 섞여 있으면") {
			it("만료 상품은 목록에서 빠지고 상시 상품은 남는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-shop-1")).id!!
				val alwaysId: Long = IntegrationUtil.persist(CoinItemEntityFixture.create(coinAmount = 100)).id!!
				val expiredId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 0),
				).id!!

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.id", hasItem(alwaysId.toInt()))
					body("data.id", not(hasItem(expiredId.toInt())))
				}
			}
		}

		context("활성 기간 한정 상품은") {
			it("목록에 노출된다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-shop-2")).id!!
				val activeId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 30),
				).id!!

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.id", hasItem(activeId.toInt()))
				}
			}
		}
	}

	describe("GET /payments/v1/coin/checkout (기간 한정 오퍼)") {

		context("만료된 기간 한정 상품으로 체크아웃하면") {
			it("400 COIN-005를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-checkout-1")).id!!
				val expiredId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 0),
				).id!!

				get("/payments/v1/coin/checkout?itemId=$expiredId") {
					bearer(accessTokenFor(userId))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "COIN-005")
				}
			}
		}

		context("활성 기간 한정 상품으로 체크아웃하면") {
			it("200으로 아이템을 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-checkout-2")).id!!
				val activeId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 30),
				).id!!

				get("/payments/v1/coin/checkout?itemId=$activeId") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.item.id", activeId.toInt())
					body("data.item.coinAmount", 200)
				}
			}
		}
	}
})
```

- [ ] **Step 2: E2E 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.coin.CoinTimeLimitedOfferE2ETest"`
Expected: PASS (4건).

- [ ] **Step 3: 마이그레이션·시드 SQL 작성** — `docs/migration/` 의 기존 형식(한국어 헤더 주석)에 맞춰 작성.

Create `docs/migration/coin_items_valid_days.sql`:
```sql
-- 코인 상품에 유저 가입시각 기준 유효일수(가입 후 N일) 컬럼 추가. NULL이면 상시 판매(기존 상품 하위호환).
ALTER TABLE coin_items ADD COLUMN valid_days INT NULL;
```

Create `docs/migration/coin_item_seed_double_welcome.sql`:
```sql
-- 신규 가입자에게 가입 후 7일간 1회 한정 2배(200코인) PG 상품. 만료는 유저별 가입시각 + 7일로 자동 계산된다.
INSERT INTO coin_items (coin_amount, price, sale_price, once_per_user, sale_channel,
                        store_product_id, valid_days, created_at, updated_at)
VALUES (200, 10000, 4900, 1, 'PG', NULL, 7, NOW(6), NOW(6));
```

- [ ] **Step 4: 전체 테스트(회귀 포함)**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 코인 상점·체크아웃·구매 E2E 포함 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/coin/CoinTimeLimitedOfferE2ETest.kt \
        docs/migration/coin_items_valid_days.sql \
        docs/migration/coin_item_seed_double_welcome.sql
git commit -m "test(coin): 기간 한정 오퍼 E2E + 마이그레이션·시드 추가"
```

---

## Self-Review

**Spec coverage:**
- `coin_items.valid_days` 컬럼 → Task 5·8. ✓
- 만료 계산(가입+N일, 저장 없음) `isOfferActiveAt` → Task 1. ✓
- `CoinItems.activeOffersAt`·`hasTimeLimitedOffer` → Task 2. ✓
- 노출 게이트(상점 만료 숨김, lazy 유저 조회) → Task 6. ✓
- 결제-전 게이트(체크아웃 만료 재검증, PG 캡처 전) → Task 7. ✓
- `UserView.createdAt` 노출(GetUserById 재사용) → Task 4. ✓
- `CoinErrorCode.COIN_ITEM_OFFER_EXPIRED`(COIN-005, 400) → Task 3. ✓
- "2배" = 별도 데이터 상품(시드) → Task 8. ✓
- PG 전용, IAP 제외 → 시드·테스트 PG 채널. ✓
- 도메인 유닛(경계) + E2E(상점·체크아웃) + 회귀 → Task 1·2·8. ✓
- 마이그레이션 ALTER + 시드 → Task 8. ✓
- 클라이언트 변경 없음 → 계획에 없음(의도적). ✓

**Placeholder scan:** 모든 코드 step은 완전한 코드 포함. TBD/TODO 없음. ✓

**Type consistency:**
- `CoinItem` 8-arg(…, validDays: Int?=null): 정의 Task 1, 투영 Task 5(순서 id·coinAmount·price·salePrice·oncePerUser·saleChannel·storeProductId·validDays 일치), fixture Task 5. ✓
- `isOfferActiveAt(userCreatedAt: LocalDateTime, now: LocalDateTime): Boolean`: Task 1 정의, Task 2·6·7 사용 시그니처 일치. ✓
- `activeOffersAt(userCreatedAt, now): CoinItems`, `hasTimeLimitedOffer(): Boolean`: Task 2 정의, Task 6 사용. ✓
- `UserView(id, email, status, createdAt: LocalDateTime)`: Task 4 정의(투영 2곳 4-arg), Task 6·7 `.createdAt` 사용. ✓
- `GetCoinCheckoutUseCase.getCheckout(userId, itemId)`: Task 7 정의, 호출자(CompleteCoinPurchaseService·PaymentsController) 동일 시그니처. ✓
- `COIN_ITEM_OFFER_EXPIRED`: Task 3 정의, Task 7 사용. ✓
