# 회원당 1회 구매 코인 패키지 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코인 상점에 회원당 한 번만 살 수 있는 코인 패키지를 추가하고, 상품별 판매 채널(PG/IAP)을 구분하며, IAP 결제를 coin_item에 연결(멱등 기록 포함)한다.

**Architecture:** 헥사고날 멀티모듈(common·core·infra·api). `coin_items`에 플래그 3개(once_per_user·sale_channel·store_product_id)를 얹고, 경로 무관 1회 제한을 원자적으로 강제하는 가드 테이블 `coin_item_purchases`(UNIQUE user+item)를 신설한다. 적립과 가드 INSERT를 한 트랜잭션으로 묶는 coin in-port `AcquirePurchasedCoinUseCase`를 PG·IAP 두 결제 경로가 공유해 경합 이중적립을 원천 차단한다. IAP는 SKU→coin_item 해석 + `iap_payments`(UNIQUE transaction_id) 멱등을 도입한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL. 테스트: Kotest(도메인 유닛) + Testcontainers E2E(`AbstractIntegrationSupport`).

## Global Constraints

- 응답 언어·주석·커밋 메시지 한국어. 코드 식별자 영어.
- `oneulsogae-backend`만 수정. 프론트/모바일/어드민 변경 금지(필요 시 안내만).
- 타입 명시(변수·반환·람다 파라미터). `LocalDateTime.now()` 직접 호출 금지(`TimeGenerator` 주입 — 단, 이 플랜의 신규 코드는 시각을 직접 다루지 않음. 가드/기록의 created_at은 `BaseEntity` JPA Auditing이 채움).
- 도메인 검증은 서비스 `if…throw` 나열 대신 도메인 모델 함수로 캡슐화.
- 도메인 간 참조는 **그 도메인 in-port** 주입(타 도메인 out-port·구현체 직접 주입 금지). → payments 서비스는 coin **in-port**(`AcquirePurchasedCoinUseCase`·`IsCoinItemPurchasedUseCase`·`GetCoinItemBySkuUseCase`)를 주입한다.
- CQRS: 조회는 `query`(dao·read model), 명령은 `command`(도메인·포트). 엔티티당 어댑터 하나.
- 코인 상품 판매채널 enum: `CoinSaleChannel { PG, IAP, BOTH }`. IAP/BOTH 상품은 `store_product_id`(SKU) 필수.
- 에러코드 신규: `PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED("PAYMENTS-006", "이미 구매한 패키지입니다.", HttpStatus.CONFLICT)`. 기존 재사용: `CoinErrorCode.COIN_ITEM_NOT_FOUND("COIN-004")`.
- 상점 엔드포인트 `/coins/v1/shop`은 이미 인증 필수(permitAll 아님) — userId 항상 존재.
- 스펙: `docs/superpowers/specs/2026-07-25-once-per-user-coin-package-design.md`.

---

## File Structure

**common**
- `common/coin/CoinSaleChannel.kt` (신규) — 판매 채널 enum + `sellableVia(channel)`.

**core**
- `coin/query/dto/CoinItem.kt` (수정) — 필드 3개 + 불변식 검증.
- `coin/query/dao/GetCoinItemDao.kt` (수정) — `findByStoreProductId`, `findShopItems`.
- `coin/query/service/port/in/GetCoinShopUseCase.kt` (수정) — `getCoinShop(userId, channel)`.
- `coin/query/service/GetCoinShopService.kt` (수정).
- `coin/query/service/port/in/GetCoinItemBySkuUseCase.kt` (신규) + `GetCoinItemBySkuService.kt` (신규).
- `coin/query/service/port/in/IsCoinItemPurchasedUseCase.kt` (신규) + `IsCoinItemPurchasedService.kt` (신규).
- `coin/query/dao/GetCoinItemPurchaseDao.kt` (신규) — `exists(userId, itemId)`.
- `coin/command/application/port/out/SaveCoinItemPurchasePort.kt` (신규).
- `coin/command/application/port/in/AcquirePurchasedCoinUseCase.kt` (신규) + `AcquirePurchasedCoinService.kt` (신규, `@Transactional`).
- `payments/command/domain/IapPayment.kt` (신규).
- `payments/command/application/port/out/SaveIapPaymentPort.kt`, `GetIapPaymentPort.kt` (신규).
- `payments/command/application/CompleteCoinPurchaseService.kt` (수정).
- `payments/command/application/VerifyIapPurchaseService.kt` (수정).
- `payments/PaymentsErrorCode.kt` (수정).

**infra**
- `coin/command/entity/CoinItemEntity.kt` (수정) — 3컬럼.
- `coin/command/entity/CoinItemPurchaseEntity.kt` (신규) + repository + `CoinItemPurchaseAdapter.kt`(Save out-port) + `GetCoinItemPurchaseDaoImpl.kt`(exists).
- `coin/query/GetCoinItemDaoImpl.kt` (수정) — 투영 3컬럼, `findByStoreProductId`, `findShopItems`.
- `coin/command/application/...` 없음(코어).
- `payments/command/entity/IapPaymentEntity.kt` (신규) + repository + `IapPaymentAdapter.kt`(Save/Get out-port).

**api**
- `coin/CoinController.kt` (수정) — shop에 channel·@LoginUser.
- `coin/response/CoinItemResponse.kt` (수정) — `oncePerUser`.

**test**
- `domain/coin/CoinItemTest.kt` (신규, Kotest) — 불변식.
- `infra/fixture/CoinItemEntityFixture.kt` (신규) — E2E 픽스처.
- `api/coin/CoinShopE2ETest.kt` (신규) — 채널 필터·구매 후 숨김.
- `api/payments/CoinCompleteOncePackageE2ETest.kt` (신규) — PG 1회 제한.
- `api/payments/IapPurchaseE2ETest.kt` (신규) — IAP SKU 해석·멱등·1회 제한.

---

## Task 1: CoinSaleChannel enum (common)

**Files:**
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinSaleChannel.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinSaleChannelTest.kt`

**Interfaces:**
- Produces: `enum class CoinSaleChannel { PG, IAP, BOTH }`; `fun sellableVia(channel: CoinSaleChannel): Boolean`.

- [ ] **Step 1: Write the failing test**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinSaleChannelTest.kt`:

```kotlin
package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.common.coin.CoinSaleChannel
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** [CoinSaleChannel.sellableVia] 판정 규칙 유닛 테스트. BOTH는 PG·IAP 모두 판매 가능. */
class CoinSaleChannelTest : DescribeSpec({

	describe("sellableVia") {
		it("PG 상품은 PG로만 판매된다") {
			CoinSaleChannel.PG.sellableVia(CoinSaleChannel.PG) shouldBe true
			CoinSaleChannel.PG.sellableVia(CoinSaleChannel.IAP) shouldBe false
		}
		it("IAP 상품은 IAP로만 판매된다") {
			CoinSaleChannel.IAP.sellableVia(CoinSaleChannel.IAP) shouldBe true
			CoinSaleChannel.IAP.sellableVia(CoinSaleChannel.PG) shouldBe false
		}
		it("BOTH 상품은 PG·IAP 모두 판매된다") {
			CoinSaleChannel.BOTH.sellableVia(CoinSaleChannel.PG) shouldBe true
			CoinSaleChannel.BOTH.sellableVia(CoinSaleChannel.IAP) shouldBe true
		}
	}
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.coin.CoinSaleChannelTest"`
Expected: FAIL — `CoinSaleChannel` 미해결(compile error).

- [ ] **Step 3: Write minimal implementation**

Create `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinSaleChannel.kt`:

```kotlin
package com.org.oneulsogae.common.coin

/**
 * 코인 상품 판매 채널. PG(웹 결제)·IAP(앱 인앱결제)를 구분하며, BOTH는 두 채널에서 모두 판매한다.
 * 상점은 요청 채널로 노출 상품을 거르고, IAP 검증은 상품이 IAP 채널인지 확인한다.
 */
enum class CoinSaleChannel {
	PG,
	IAP,
	BOTH,
	;

	/** 이 상품이 [channel] 채널로 판매되는지 여부. BOTH는 어떤 채널이든 true. */
	fun sellableVia(channel: CoinSaleChannel): Boolean = this == BOTH || this == channel
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.coin.CoinSaleChannelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinSaleChannel.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinSaleChannelTest.kt
git commit -m "feat(coin): 코인 상품 판매 채널 enum(CoinSaleChannel) 추가"
```

---

## Task 2: CoinItem 도메인 필드·불변식 (core)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dto/CoinItem.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinItemTest.kt`

**Interfaces:**
- Consumes: `CoinSaleChannel` (Task 1).
- Produces: `data class CoinItem(id, coinAmount, price, salePrice, oncePerUser: Boolean = false, saleChannel: CoinSaleChannel = CoinSaleChannel.PG, storeProductId: String? = null)`; `CoinItem.create(coinAmount, price, salePrice, oncePerUser, saleChannel, storeProductId)`; 기존 `pricePerCoin`·`discountRate` 유지.

주의: `GetCoinItemDaoImpl`의 QueryDSL `Projections.constructor(CoinItem::class.java, id, coinAmount, price, salePrice)`는 4-arg 생성자를 찾는다. 새 필드는 **기본값**을 줘 4-arg 투영이 계속 컴파일되게 하고, Task 6에서 6-arg 투영으로 확장한다.

- [ ] **Step 1: Write the failing test**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinItemTest.kt`:

```kotlin
package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [CoinItem] 생성 규칙 유닛 테스트.
 * 판매 채널·1회 제한 필드와 불변식(IAP/BOTH 상품은 store_product_id 필수)을 검증한다.
 */
class CoinItemTest : DescribeSpec({

	describe("create") {
		it("PG 전용 상품은 store_product_id 없이 만들 수 있다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 100, price = 12000, salePrice = 10000,
				oncePerUser = false, saleChannel = CoinSaleChannel.PG, storeProductId = null,
			)
			item.saleChannel shouldBe CoinSaleChannel.PG
			item.oncePerUser shouldBe false
			item.storeProductId shouldBe null
		}

		it("IAP 상품은 store_product_id가 있으면 만들 수 있다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 300, price = 30000, salePrice = 19900,
				oncePerUser = true, saleChannel = CoinSaleChannel.IAP, storeProductId = "com.oneulsogae.coins.welcome",
			)
			item.oncePerUser shouldBe true
			item.storeProductId shouldBe "com.oneulsogae.coins.welcome"
		}

		it("IAP 상품인데 store_product_id가 없으면 예외를 던진다") {
			shouldThrow<IllegalArgumentException> {
				CoinItem.create(
					coinAmount = 300, price = 30000, salePrice = 19900,
					oncePerUser = true, saleChannel = CoinSaleChannel.IAP, storeProductId = null,
				)
			}
		}

		it("BOTH 상품인데 store_product_id가 없으면 예외를 던진다") {
			shouldThrow<IllegalArgumentException> {
				CoinItem.create(
					coinAmount = 300, price = 30000, salePrice = 19900,
					oncePerUser = false, saleChannel = CoinSaleChannel.BOTH, storeProductId = "  ",
				)
			}
		}
	}
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.coin.CoinItemTest"`
Expected: FAIL — `create`의 새 파라미터 미해결(compile error).

- [ ] **Step 3: Write minimal implementation**

Replace `CoinItem.kt` 전체:

```kotlin
package com.org.oneulsogae.core.coin.query.dto

import com.org.oneulsogae.common.coin.CoinSaleChannel
import kotlin.math.roundToInt

/**
 * 판매(구매) 가능한 코인 상품 도메인 모델.
 * 상품 구매 시 [coinAmount]만큼의 코인이 할인가([salePrice])에 지급되며, [price]는 정가다.
 * [oncePerUser]가 true면 회원당 한 번만 구매할 수 있는 패키지다.
 * [saleChannel]은 판매 채널(PG·IAP·BOTH)이며, IAP로 팔리는 상품(IAP·BOTH)은 스토어 SKU([storeProductId])가 필수다.
 * 영속성은 [com.org.oneulsogae.infra.coin.command.entity.CoinItemEntity]가 담당한다.
 */
data class CoinItem(
	val id: Long = 0,
	val coinAmount: Int,
	val price: Int,
	val salePrice: Int,
	val oncePerUser: Boolean = false,
	val saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
	val storeProductId: String? = null,
) {

	/** 코인 1개당 실제 결제 가격. (salePrice / coinAmount) */
	val pricePerCoin: Double
		get() = if (coinAmount <= 0) 0.0 else salePrice.toDouble() / coinAmount

	/**
	 * 정가([price]) 대비 할인율(%)을 반환한다. (반올림한 정수 %)
	 * 정가가 0 이하이거나 할인가가 정가 이상이면 0을 반환한다.
	 */
	val discountRate: Int
		get() {
			if (price <= 0 || salePrice >= price) return 0
			val rate: Double = (price - salePrice).toDouble() / price * 100
			return rate.roundToInt()
		}

	companion object {

		/**
		 * 새 코인 상품을 생성한다.
		 * IAP로 팔리는 상품(IAP·BOTH)은 스토어 SKU([storeProductId])가 반드시 있어야 한다(공백 불가).
		 */
		fun create(
			coinAmount: Int,
			price: Int,
			salePrice: Int,
			oncePerUser: Boolean = false,
			saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
			storeProductId: String? = null,
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
			)
		}
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.coin.CoinItemTest"`
Expected: PASS. (기존 `Projections.constructor(..., id, coinAmount, price, salePrice)` 4-arg 투영은 새 필드 기본값 덕분에 계속 컴파일됨)

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dto/CoinItem.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinItemTest.kt
git commit -m "feat(coin): CoinItem에 판매채널·1회제한·SKU 필드와 불변식 추가"
```

---

## Task 3: coin_items 엔티티 컬럼 + 픽스처 (infra)

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/command/entity/CoinItemEntity.kt`
- Create: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/CoinItemEntityFixture.kt`

**Interfaces:**
- Consumes: `CoinSaleChannel` (Task 1).
- Produces: `CoinItemEntity(coinAmount, price, salePrice, oncePerUser=false, saleChannel=PG, storeProductId=null)`; `CoinItemEntityFixture.create(...)` 동일 시그니처 반환.

이 태스크는 DB 스키마(컬럼) 확장이라 자체 단위 테스트는 없다. E2E(Task 12~14)가 실사용을 검증한다. 컴파일·컨텍스트 기동으로 확인한다.

- [ ] **Step 1: 엔티티에 컬럼 3개 추가**

Replace `CoinItemEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.coin.command.entity

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * coin_items 테이블 영속성 엔티티. 판매(구매) 가능한 코인 상품을 정의한다.
 * 각 상품은 지급 코인 개수·정가([price])·할인가([salePrice])와 함께,
 * 회원당 1회 제한([oncePerUser])·판매 채널([saleChannel])·스토어 SKU([storeProductId])를 가진다.
 * 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "coin_items")
class CoinItemEntity(
	/** 상품 구매 시 지급되는 코인 개수. */
	@Column(name = "coin_amount", nullable = false)
	var coinAmount: Int,

	/** 정가. */
	@Column(name = "price", nullable = false)
	var price: Int,

	/** 할인가. (실제 결제 가격) */
	@Column(name = "sale_price", nullable = false)
	var salePrice: Int,

	/** 회원당 1회만 구매 가능한 패키지 여부. */
	@Column(name = "once_per_user", nullable = false)
	var oncePerUser: Boolean = false,

	/** 판매 채널(PG·IAP·BOTH). */
	@Enumerated(EnumType.STRING)
	@Column(name = "sale_channel", nullable = false, columnDefinition = "varchar(10)")
	var saleChannel: CoinSaleChannel = CoinSaleChannel.PG,

	/** 스토어 상품 id(SKU). IAP 검증이 SKU→coin_item 해석에 쓴다. PG 전용 상품은 null. 유니크. */
	@Column(name = "store_product_id", unique = true)
	var storeProductId: String? = null,
) : BaseEntity()
```

- [ ] **Step 2: 픽스처 생성**

Create `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/CoinItemEntityFixture.kt`:

```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.infra.coin.command.entity.CoinItemEntity

/**
 * [CoinItemEntity] 테스트 픽스처. 기본은 100코인·정가 12000·할인가 10000의 PG 전용 일반 상품이다.
 * 1회 패키지·IAP 상품은 인자로 지정한다.
 */
object CoinItemEntityFixture {

	fun create(
		coinAmount: Int = 100,
		price: Int = 12000,
		salePrice: Int = 10000,
		oncePerUser: Boolean = false,
		saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
		storeProductId: String? = null,
	): CoinItemEntity =
		CoinItemEntity(
			coinAmount = coinAmount,
			price = price,
			salePrice = salePrice,
			oncePerUser = oncePerUser,
			saleChannel = saleChannel,
			storeProductId = storeProductId,
		)
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-infra:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/command/entity/CoinItemEntity.kt \
        oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/CoinItemEntityFixture.kt
git commit -m "feat(coin): coin_items에 판매채널·1회제한·SKU 컬럼과 테스트 픽스처 추가"
```

---

## Task 4: coin_item_purchases 가드 (엔티티·포트·어댑터, infra+core)

**Files:**
- Create (core): `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/command/application/port/out/SaveCoinItemPurchasePort.kt`
- Create (core): `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dao/GetCoinItemPurchaseDao.kt`
- Create (infra): `.../infra/coin/command/entity/CoinItemPurchaseEntity.kt`, `.../repository/CoinItemPurchaseJpaRepository.kt`, `.../command/adapter/CoinItemPurchaseAdapter.kt`, `.../coin/query/GetCoinItemPurchaseDaoImpl.kt`

**Interfaces:**
- Produces (core out-port): `interface SaveCoinItemPurchasePort { fun save(userId: Long, itemId: Long) }`.
- Produces (core query dao): `interface GetCoinItemPurchaseDao { fun exists(userId: Long, itemId: Long): Boolean }`.
- Produces (infra): `CoinItemPurchaseEntity(userId, itemId)` with `UNIQUE(user_id, item_id)`.

DB 스키마·어댑터 태스크라 단위 테스트 없음. Task 12~14 E2E가 검증한다. 컴파일·기동으로 확인한다.

- [ ] **Step 1: core out-port·dao 인터페이스 생성**

Create `SaveCoinItemPurchasePort.kt`:

```kotlin
package com.org.oneulsogae.core.coin.command.application.port.out

/**
 * 회원당 1회 구매 코인 패키지의 구매 가드 기록 저장 out-port.
 * (user_id, item_id) 유니크라 이미 있으면 저장 시 DataIntegrityViolationException이 발생한다 —
 * 이 위반이 경로 무관 이중구매를 막는 최종 방어선이다.
 */
interface SaveCoinItemPurchasePort {

	fun save(userId: Long, itemId: Long)
}
```

Create `GetCoinItemPurchaseDao.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.dao

/** 코인 패키지 구매 가드 조회 dao. 상점 필터·구매 선검사에 쓴다. */
interface GetCoinItemPurchaseDao {

	/** 사용자가 해당 상품을 이미 구매했는지 여부. */
	fun exists(userId: Long, itemId: Long): Boolean
}
```

- [ ] **Step 2: infra 엔티티·리포지토리·어댑터·dao 구현 생성**

Create `CoinItemPurchaseEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.coin.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 회원당 1회 구매 코인 패키지의 구매 가드 기록. once_per_user 상품을 실제 적립 성공한 시점에 저장한다.
 * (user_id, item_id) 유니크가 경로(PG·IAP) 무관 이중구매를 원자적으로 막는다.
 */
@Entity
@Table(
	name = "coin_item_purchases",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_coin_item_purchases_user_item", columnNames = ["user_id", "item_id"]),
	],
)
class CoinItemPurchaseEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "item_id", nullable = false)
	val itemId: Long,
) : BaseEntity()
```

Create `CoinItemPurchaseJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.coin.command.repository

import com.org.oneulsogae.infra.coin.command.entity.CoinItemPurchaseEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 코인 패키지 구매 가드 리포지토리. 도메인 포트는 어댑터가 구현한다. */
interface CoinItemPurchaseJpaRepository : JpaRepository<CoinItemPurchaseEntity, Long> {

	fun existsByUserIdAndItemId(userId: Long, itemId: Long): Boolean
}
```

Create `CoinItemPurchaseAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.coin.command.adapter

import com.org.oneulsogae.core.coin.command.application.port.out.SaveCoinItemPurchasePort
import com.org.oneulsogae.infra.coin.command.entity.CoinItemPurchaseEntity
import com.org.oneulsogae.infra.coin.command.repository.CoinItemPurchaseJpaRepository
import org.springframework.stereotype.Component

/**
 * [CoinItemPurchaseEntity] command 영속성 어댑터. 구매 가드 저장([SaveCoinItemPurchasePort])을 구현한다.
 * (user_id, item_id) 유니크 위반은 saveAndFlush 시점에 DataIntegrityViolationException으로 즉시 표면화한다.
 */
@Component
class CoinItemPurchaseAdapter(
	private val coinItemPurchaseJpaRepository: CoinItemPurchaseJpaRepository,
) : SaveCoinItemPurchasePort {

	override fun save(userId: Long, itemId: Long) {
		// saveAndFlush로 유니크 위반을 이 트랜잭션 안에서 즉시 던지게 한다(호출 서비스가 잡아 409 매핑).
		coinItemPurchaseJpaRepository.saveAndFlush(CoinItemPurchaseEntity(userId = userId, itemId = itemId))
	}
}
```

Create `GetCoinItemPurchaseDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.coin.query

import com.org.oneulsogae.core.coin.query.dao.GetCoinItemPurchaseDao
import com.org.oneulsogae.infra.coin.command.repository.CoinItemPurchaseJpaRepository
import org.springframework.stereotype.Component

/** [GetCoinItemPurchaseDao]의 Spring Data 파생 쿼리 구현. */
@Component
class GetCoinItemPurchaseDaoImpl(
	private val coinItemPurchaseJpaRepository: CoinItemPurchaseJpaRepository,
) : GetCoinItemPurchaseDao {

	override fun exists(userId: Long, itemId: Long): Boolean =
		coinItemPurchaseJpaRepository.existsByUserIdAndItemId(userId, itemId)
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/command/application/port/out/SaveCoinItemPurchasePort.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/dao/GetCoinItemPurchaseDao.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/command/entity/CoinItemPurchaseEntity.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/command/repository/CoinItemPurchaseJpaRepository.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/command/adapter/CoinItemPurchaseAdapter.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/query/GetCoinItemPurchaseDaoImpl.kt
git commit -m "feat(coin): 1회 구매 가드 테이블(coin_item_purchases) 엔티티·포트·어댑터 추가"
```

---

## Task 5: AcquirePurchasedCoinUseCase — 적립+가드 원자 (core)

**Files:**
- Create: `.../coin/command/application/port/in/AcquirePurchasedCoinUseCase.kt`
- Create: `.../coin/command/application/AcquirePurchasedCoinService.kt`
- Test: E2E로 검증(Task 12~14). 서비스 단위 테스트는 두지 않음(기존 command 서비스도 E2E로 검증하는 관행).

**Interfaces:**
- Consumes: `AcquireCoinUseCase.acquire(userId, AcquireCoinCommand(amount, coinType=PURCHASE))` → `CoinBalance`; `SaveCoinItemPurchasePort.save(userId, itemId)` (Task 4); `CoinItem` (Task 2); `PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED` (Task 9 — 이 태스크가 먼저면 Task 9를 먼저 하거나 함께 커밋).
- Produces: `interface AcquirePurchasedCoinUseCase { fun acquire(userId: Long, item: CoinItem): CoinBalance }`.

> 의존 순서: 이 태스크는 `PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED`를 참조한다. **Task 9(에러코드 추가)를 먼저 수행**하거나 이 태스크에 포함해 함께 커밋한다. 아래 Step에 에러코드 추가를 포함한다.

- [ ] **Step 1: 에러코드 추가 (PaymentsErrorCode)**

Modify `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/PaymentsErrorCode.kt` — 마지막 항목 뒤에 추가:

```kotlin
	/** 같은 paymentKey가 이미 접수돼 승인 대기(PENDING) 중이거나 다른 사용자의 결제다. 재생하지 않는다. */
	PAYMENT_ALREADY_RECEIVED("PAYMENTS-005", "이미 접수된 결제입니다. 잠시 후 다시 확인해주세요.", HttpStatus.CONFLICT),

	/** 회원당 1회 구매 패키지를 이미 구매함. (PG·IAP 공통) */
	COIN_PACKAGE_ALREADY_PURCHASED("PAYMENTS-006", "이미 구매한 패키지입니다.", HttpStatus.CONFLICT),
}
```

(기존 마지막 줄 `PAYMENT_ALREADY_RECEIVED(...)` 뒤 세미콜론/닫는 중괄호 위치에 맞춰 삽입.)

- [ ] **Step 2: in-port 생성**

Create `AcquirePurchasedCoinUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.coin.command.application.port.`in`

import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem

/**
 * 코인 상품 구매 적립 인포트. 코인 적립과 (1회 패키지면) 구매 가드 기록을 **한 트랜잭션**에서 처리한다.
 * 가드 유니크 위반(경합 이중구매)은 적립까지 롤백돼 이중적립이 원천 차단된다.
 * PG·IAP 결제 경로가 코인 구매 적립에 이 인포트를 공유한다.
 */
interface AcquirePurchasedCoinUseCase {

	/** [item]을 [userId]에게 적립하고 갱신된 잔액을 반환한다. [item.oncePerUser]면 구매 가드도 함께 기록한다. */
	fun acquire(userId: Long, item: CoinItem): CoinBalance
}
```

- [ ] **Step 3: 구현 서비스 생성**

Create `AcquirePurchasedCoinService.kt`:

```kotlin
package com.org.oneulsogae.core.coin.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquirePurchasedCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.coin.command.application.port.out.SaveCoinItemPurchasePort
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcquirePurchasedCoinUseCase] 구현.
 * 코인 적립([AcquireCoinUseCase])과 1회 패키지 구매 가드 기록([SaveCoinItemPurchasePort])을 **한 트랜잭션**에서 처리한다.
 * 가드 (user_id, item_id) 유니크 위반이면 트랜잭션이 롤백돼 적립도 취소되고 409([PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED])로 매핑한다.
 * (이 원자성이 PG·IAP 경로의 경합 이중적립을 원천 차단한다)
 */
@Service
class AcquirePurchasedCoinService(
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val saveCoinItemPurchasePort: SaveCoinItemPurchasePort,
) : AcquirePurchasedCoinUseCase {

	@Transactional
	override fun acquire(userId: Long, item: CoinItem): CoinBalance {
		val balance: CoinBalance = acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(amount = item.coinAmount, coinType = CoinGetType.PURCHASE),
		)
		if (item.oncePerUser) {
			try {
				saveCoinItemPurchasePort.save(userId, item.id)
			} catch (e: DataIntegrityViolationException) {
				// 선검사와 적립 사이 경합으로 가드가 먼저 들어간 경우. 트랜잭션 롤백 → 적립 취소.
				throw BusinessException(PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED)
			}
		}
		return balance
	}
}
```

주의: `AcquireCoinUseCase.acquire`는 자체 `@Transactional`이다. 이 서비스도 `@Transactional`이라 같은 스레드에서 기존 트랜잭션에 참여(전파 REQUIRED 기본)해 한 트랜잭션으로 묶인다. 따라서 가드 위반 시 적립도 롤백된다.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/PaymentsErrorCode.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/command/application/port/in/AcquirePurchasedCoinUseCase.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/command/application/AcquirePurchasedCoinService.kt
git commit -m "feat(coin): 적립+1회가드 원자 처리 AcquirePurchasedCoinUseCase 추가(PAYMENTS-006)"
```

---

## Task 6: 코인 상점 채널 필터·구매 숨김 조회 (core+infra)

**Files:**
- Modify (core): `coin/query/dao/GetCoinItemDao.kt`, `coin/query/service/port/in/GetCoinShopUseCase.kt`, `coin/query/service/GetCoinShopService.kt`
- Create (core): `coin/query/service/port/in/GetCoinItemBySkuUseCase.kt`, `coin/query/service/GetCoinItemBySkuService.kt`, `coin/query/service/port/in/IsCoinItemPurchasedUseCase.kt`, `coin/query/service/IsCoinItemPurchasedService.kt`
- Modify (infra): `coin/query/GetCoinItemDaoImpl.kt`

**Interfaces:**
- Consumes: `CoinSaleChannel` (Task 1), `CoinItem` (Task 2), `GetCoinItemPurchaseDao.exists` (Task 4), `CoinErrorCode.COIN_ITEM_NOT_FOUND` (기존).
- Produces:
  - `GetCoinItemDao.findByStoreProductId(sku: String): CoinItem?`
  - `GetCoinItemDao.findShopItems(userId: Long, channel: CoinSaleChannel): CoinItems`
  - `GetCoinShopUseCase.getCoinShop(userId: Long, channel: CoinSaleChannel): CoinItems`
  - `GetCoinItemBySkuUseCase.getBySku(sku: String): CoinItem`
  - `IsCoinItemPurchasedUseCase.isPurchased(userId: Long, itemId: Long): Boolean`

DB 조인 쿼리라 E2E(Task 12)가 실사용을 검증한다. 여기서는 구현 + 컴파일·기동으로 확인한다.

- [ ] **Step 1: GetCoinItemDao 인터페이스 확장**

Replace `GetCoinItemDao.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.dao

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems

/** 코인 상품 조회 dao. (코인 상점·체크아웃·IAP 해석 read model 반환) */
interface GetCoinItemDao {

	/**
	 * 상점에 노출할 코인 상품을 조회한다.
	 * [channel] 또는 BOTH 채널 상품만 반환하며, once_per_user 상품 중 [userId]가 이미 구매한 것은 제외한다.
	 */
	fun findShopItems(userId: Long, channel: CoinSaleChannel): CoinItems

	/** 코인 상품 한 건을 id로 조회한다. 없으면 null. */
	fun findById(itemId: Long): CoinItem?

	/** 스토어 SKU로 코인 상품을 조회한다. IAP 검증의 SKU→coin_item 해석에 쓴다. 없으면 null. */
	fun findByStoreProductId(storeProductId: String): CoinItem?
}
```

(기존 `findAll()` 제거 — `GetCoinShopService`만 쓰던 메서드이며 `findShopItems`로 대체된다.)

- [ ] **Step 2: GetCoinShopUseCase·Service 시그니처 변경**

Replace `GetCoinShopUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.service.port.`in`

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dto.CoinItems

/** 코인 상점에 노출할 코인 상품 목록을 조회하는 인포트(유스케이스). */
interface GetCoinShopUseCase {

	/** [channel]·BOTH 상품 중 [userId]가 아직 못 산 것(1회 패키지 구매분 제외)을 반환한다. */
	fun getCoinShop(userId: Long, channel: CoinSaleChannel): CoinItems
}
```

Replace `GetCoinShopService.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinShopUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetCoinShopUseCase] 구현.
 * 요청 채널(+BOTH) 상품 중, 이미 구매한 1회 패키지를 제외하고 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetCoinShopService(
	private val getCoinItemDao: GetCoinItemDao,
) : GetCoinShopUseCase {

	override fun getCoinShop(userId: Long, channel: CoinSaleChannel): CoinItems =
		getCoinItemDao.findShopItems(userId, channel)
}
```

- [ ] **Step 3: SKU 조회 in-port·service 생성**

Create `GetCoinItemBySkuUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.service.port.`in`

import com.org.oneulsogae.core.coin.query.dto.CoinItem

/** 스토어 SKU로 코인 상품을 조회하는 인포트. IAP 검증이 SKU→coin_item 해석에 쓴다. */
interface GetCoinItemBySkuUseCase {

	/** [storeProductId](SKU)에 해당하는 코인 상품. 없으면 COIN_ITEM_NOT_FOUND. */
	fun getBySku(storeProductId: String): CoinItem
}
```

Create `GetCoinItemBySkuService.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.core.coin.CoinErrorCode
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinItemBySkuUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetCoinItemBySkuUseCase] 구현. SKU로 코인 상품을 조회하고 없으면 [CoinErrorCode.COIN_ITEM_NOT_FOUND]. */
@Service
@Transactional(readOnly = true)
class GetCoinItemBySkuService(
	private val getCoinItemDao: GetCoinItemDao,
) : GetCoinItemBySkuUseCase {

	override fun getBySku(storeProductId: String): CoinItem =
		getCoinItemDao.findByStoreProductId(storeProductId)
			?: throw BusinessException(CoinErrorCode.COIN_ITEM_NOT_FOUND)
}
```

- [ ] **Step 4: 구매 선검사 in-port·service 생성**

Create `IsCoinItemPurchasedUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.service.port.`in`

/** 1회 패키지 구매 선검사 인포트. 결제 서비스가 승인·검증 전에 이미 구매를 걸러 이른 409를 낸다. */
interface IsCoinItemPurchasedUseCase {

	fun isPurchased(userId: Long, itemId: Long): Boolean
}
```

Create `IsCoinItemPurchasedService.kt`:

```kotlin
package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.core.coin.query.dao.GetCoinItemPurchaseDao
import com.org.oneulsogae.core.coin.query.service.port.`in`.IsCoinItemPurchasedUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [IsCoinItemPurchasedUseCase] 구현. 구매 가드 존재 여부를 조회한다. */
@Service
@Transactional(readOnly = true)
class IsCoinItemPurchasedService(
	private val getCoinItemPurchaseDao: GetCoinItemPurchaseDao,
) : IsCoinItemPurchasedUseCase {

	override fun isPurchased(userId: Long, itemId: Long): Boolean =
		getCoinItemPurchaseDao.exists(userId, itemId)
}
```

- [ ] **Step 5: GetCoinItemDaoImpl 구현 교체**

Replace `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/query/GetCoinItemDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.coin.query

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 코인 상품 조회 dao([GetCoinItemDao])의 QueryDSL 구현.
 * 상점 목록은 요청 채널(+BOTH) 상품을 [CoinItem] read model로 투영하되, 이미 산 1회 패키지는 left join으로 걸러낸다.
 */
@Component
class GetCoinItemDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCoinItemDao {

	override fun findShopItems(userId: Long, channel: CoinSaleChannel): CoinItems {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		val purchase: QCoinItemPurchaseEntity = QCoinItemPurchaseEntity.coinItemPurchaseEntity
		return CoinItems(
			queryFactory
				.select(projection(coinItem))
				.from(coinItem)
				// 이 사용자의 이 상품 구매 가드를 붙여, once_per_user + 구매분을 where에서 제외한다.
				.leftJoin(purchase)
				.on(purchase.itemId.eq(coinItem.id).and(purchase.userId.eq(userId)))
				.where(
					// 요청 채널 또는 BOTH.
					coinItem.saleChannel.eq(channel).or(coinItem.saleChannel.eq(CoinSaleChannel.BOTH)),
					// once_per_user이고 이미 구매(purchase 매칭)면 제외.
					coinItem.oncePerUser.isFalse.or(purchase.id.isNull),
				)
				.fetch(),
		)
	}

	override fun findById(itemId: Long): CoinItem? {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		return queryFactory
			.select(projection(coinItem))
			.from(coinItem)
			.where(coinItem.id.eq(itemId))
			.fetchOne()
	}

	override fun findByStoreProductId(storeProductId: String): CoinItem? {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		return queryFactory
			.select(projection(coinItem))
			.from(coinItem)
			.where(coinItem.storeProductId.eq(storeProductId))
			.fetchOne()
	}

	/** 6-arg CoinItem 생성자 투영. (id, coinAmount, price, salePrice, oncePerUser, saleChannel, storeProductId) */
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
		)
}
```

주의: `QCoinItemPurchaseEntity`는 Task 4 엔티티에서 kapt가 생성한다. 이 태스크 빌드 전 Task 4가 컴파일돼 있어야 한다(순서 준수).

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/coin/query/ \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/coin/query/GetCoinItemDaoImpl.kt
git commit -m "feat(coin): 상점 채널 필터·1회패키지 숨김 조회 + SKU/구매 선검사 in-port 추가"
```

---

## Task 7: 상점 API — channel 파라미터·oncePerUser 응답 (api)

**Files:**
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/coin/CoinController.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/coin/response/CoinItemResponse.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/coin/CoinShopE2ETest.kt` (Task 12에서 작성)

**Interfaces:**
- Consumes: `GetCoinShopUseCase.getCoinShop(userId, channel)` (Task 6), `CoinSaleChannel` (Task 1).
- Produces: `GET /coins/v1/shop?channel={PG|IAP|BOTH}` 응답에 `oncePerUser: Boolean` 포함.

- [ ] **Step 1: CoinItemResponse에 oncePerUser 추가**

Replace `CoinItemResponse.kt`:

```kotlin
package com.org.oneulsogae.api.coin.response

import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems

/** 코인 상점에 노출할 코인 상품 응답. */
data class CoinItemResponse(
	val id: Long,
	val coinAmount: Int,
	/** 정가. */
	val price: Int,
	/** 할인가. (실제 결제 가격) */
	val salePrice: Int,
	/** 코인 1개당 가격. (할인가 기준, 소수점 제외 자연수 부분만) */
	val pricePerCoin: Int,
	/** 정가 대비 할인율(%). */
	val discountRate: Int,
	/** 회원당 1회만 구매 가능한 패키지 여부. (클라이언트가 "한정" 뱃지 표시) */
	val oncePerUser: Boolean,
) {
	companion object {
		fun of(coinItem: CoinItem): CoinItemResponse =
			CoinItemResponse(
				id = coinItem.id,
				coinAmount = coinItem.coinAmount,
				price = coinItem.price,
				salePrice = coinItem.salePrice,
				pricePerCoin = coinItem.pricePerCoin.toInt(),
				discountRate = coinItem.discountRate,
				oncePerUser = coinItem.oncePerUser,
			)

		/** 코인 상품 목록을 응답 목록으로 변환한다. */
		fun listOf(coinItems: CoinItems): List<CoinItemResponse> =
			coinItems.values.map { of(it) }
	}
}
```

- [ ] **Step 2: CoinController.getCoinShop 시그니처 변경**

Modify `CoinController.kt` — import에 추가:

```kotlin
import com.org.oneulsogae.common.coin.CoinSaleChannel
```

`getCoinShop` 메서드 교체:

```kotlin
	/** 코인 상점에 노출할 코인 상품 목록을 조회한다. (요청 채널·BOTH 상품 중 이미 산 1회 패키지 제외) */
	@Operation(
		summary = "코인 상점 조회",
		description = "요청 채널(channel=PG|IAP|BOTH)로 파는 코인 상품 목록을 조회한다. 앱은 IAP, 웹은 PG를 넘긴다. 회원당 1회 패키지 중 이미 구매한 상품은 제외된다.",
	)
	@GetMapping("/shop")
	fun getCoinShop(
		@LoginUser user: AuthUser,
		@RequestParam channel: CoinSaleChannel,
	): ApiResponse<List<CoinItemResponse>> =
		ApiResponse.success(CoinItemResponse.listOf(getCoinShopUseCase.getCoinShop(user.id, channel)))
```

(`@LoginUser`·`AuthUser`·`@RequestParam`은 이미 import돼 있다 — 파일 상단 확인. 없으면 추가.)

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/coin/CoinController.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/coin/response/CoinItemResponse.kt
git commit -m "feat(coin): 상점 조회에 channel 파라미터·oncePerUser 응답 필드 추가"
```

---

## Task 8: iap_payments 도메인·포트·어댑터 (core+infra)

**Files:**
- Create (core): `payments/command/domain/IapPayment.kt`, `payments/command/application/port/out/SaveIapPaymentPort.kt`, `payments/command/application/port/out/GetIapPaymentPort.kt`
- Create (infra): `payments/command/entity/IapPaymentEntity.kt`, `payments/command/repository/IapPaymentJpaRepository.kt`, `payments/command/adapter/IapPaymentAdapter.kt`

**Interfaces:**
- Consumes: `StorePlatform` (기존), `PaymentStatus` (기존).
- Produces:
  - `class IapPayment(id: Long? = null, userId, itemId, platform: StorePlatform, productId, transactionId, coinAmount, status: PaymentStatus)`
  - `SaveIapPaymentPort.save(iapPayment: IapPayment): IapPayment`
  - `GetIapPaymentPort.findByTransactionId(transactionId: String): IapPayment?`

DB·어댑터 태스크라 단위 테스트 없음. Task 14 E2E가 검증. 컴파일·기동으로 확인.

- [ ] **Step 1: core 도메인·포트 생성**

Create `IapPayment.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.domain

/**
 * 인앱결제(IAP) 코인 구매 기록. 스토어 영수증 검증·적립을 한 건으로 남긴다.
 * [transactionId]는 스토어 거래 식별자로 유니크다 — 같은 영수증 재검증을 [findByTransactionId]로 걸러 재적립을 막는다.
 * [status]는 스토어 결제 상태 축이며, 코인 지급 원장(coin_histories)과는 다른 축이다.
 */
class IapPayment(
	val id: Long? = null,
	val userId: Long,
	val itemId: Long,
	val platform: StorePlatform,
	val productId: String,
	val transactionId: String,
	val coinAmount: Int,
	val status: PaymentStatus,
)
```

Create `SaveIapPaymentPort.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application.port.out

import com.org.oneulsogae.core.payments.command.domain.IapPayment

/** 인앱결제 기록 저장 out-port. transaction_id 유니크 위반 시 DataIntegrityViolationException. */
interface SaveIapPaymentPort {

	fun save(iapPayment: IapPayment): IapPayment
}
```

Create `GetIapPaymentPort.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application.port.out

import com.org.oneulsogae.core.payments.command.domain.IapPayment

/** 인앱결제 기록 조회 out-port. 같은 transaction_id 재검증을 멱등 처리하는 데 쓴다. */
interface GetIapPaymentPort {

	fun findByTransactionId(transactionId: String): IapPayment?
}
```

- [ ] **Step 2: infra 엔티티·리포지토리·어댑터 생성**

Create `IapPaymentEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.payments.command.entity

import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import com.org.oneulsogae.core.payments.command.domain.StorePlatform
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 인앱결제 코인 구매 기록 한 건. 스토어 거래 식별자(transaction_id)가 유니크라 같은 영수증 재검증을 멱등 처리한다.
 * (user_id) 인덱스로 사용자별 IAP 결제 내역 조회를 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "iap_payments",
	indexes = [
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class IapPaymentEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** SKU로 해석한 코인 상품 id(coin_items). */
	@Column(name = "item_id", nullable = false)
	val itemId: Long,

	@Enumerated(EnumType.STRING)
	@Column(name = "platform", nullable = false, columnDefinition = "varchar(10)")
	val platform: StorePlatform,

	/** 스토어 상품 id(SKU). */
	@Column(name = "product_id", nullable = false)
	val productId: String,

	/** 스토어 거래 식별자. 재검증 멱등을 위해 유니크. */
	@Column(name = "transaction_id", nullable = false, unique = true)
	val transactionId: String,

	/** 지급 코인 개수(스냅샷). */
	@Column(name = "coin_amount", nullable = false)
	val coinAmount: Int,

	/** 스토어 결제 상태. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	val status: PaymentStatus,
) : BaseEntity()
```

Create `IapPaymentJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.payments.command.repository

import com.org.oneulsogae.infra.payments.command.entity.IapPaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 인앱결제 기록 리포지토리. 도메인 포트는 어댑터가 구현한다. */
interface IapPaymentJpaRepository : JpaRepository<IapPaymentEntity, Long> {

	fun findByTransactionId(transactionId: String): IapPaymentEntity?
}
```

Create `IapPaymentAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.payments.command.adapter

import com.org.oneulsogae.core.payments.command.application.port.out.GetIapPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.SaveIapPaymentPort
import com.org.oneulsogae.core.payments.command.domain.IapPayment
import com.org.oneulsogae.infra.payments.command.entity.IapPaymentEntity
import com.org.oneulsogae.infra.payments.command.repository.IapPaymentJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * [IapPaymentEntity] command 영속성 어댑터. 저장([SaveIapPaymentPort])·조회([GetIapPaymentPort]) out-port를 구현한다.
 * transaction_id 유니크 위반은 saveAndFlush 시점에 DataIntegrityViolationException으로 표면화한다(호출 서비스가 멱등 처리).
 */
@Component
class IapPaymentAdapter(
	private val iapPaymentJpaRepository: IapPaymentJpaRepository,
) : SaveIapPaymentPort, GetIapPaymentPort {

	override fun save(iapPayment: IapPayment): IapPayment =
		iapPaymentJpaRepository.saveAndFlush(
			IapPaymentEntity(
				userId = iapPayment.userId,
				itemId = iapPayment.itemId,
				platform = iapPayment.platform,
				productId = iapPayment.productId,
				transactionId = iapPayment.transactionId,
				coinAmount = iapPayment.coinAmount,
				status = iapPayment.status,
			),
		).toDomain()

	@Transactional(readOnly = true)
	override fun findByTransactionId(transactionId: String): IapPayment? =
		iapPaymentJpaRepository.findByTransactionId(transactionId)?.toDomain()

	private fun IapPaymentEntity.toDomain(): IapPayment =
		IapPayment(
			id = id,
			userId = userId,
			itemId = itemId,
			platform = platform,
			productId = productId,
			transactionId = transactionId,
			coinAmount = coinAmount,
			status = status,
		)
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/domain/IapPayment.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/out/SaveIapPaymentPort.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/out/GetIapPaymentPort.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/entity/IapPaymentEntity.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/repository/IapPaymentJpaRepository.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/adapter/IapPaymentAdapter.kt
git commit -m "feat(payments): IAP 결제 기록(iap_payments) 도메인·포트·어댑터 추가"
```

---

## Task 9: PG 결제완료에 1회 제한·원자 적립 연결 (core)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/CompleteCoinPurchaseService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/CoinCompleteOncePackageE2ETest.kt` (Task 13에서 작성)

**Interfaces:**
- Consumes: `IsCoinItemPurchasedUseCase.isPurchased` (Task 6), `AcquirePurchasedCoinUseCase.acquire(userId, item)` (Task 5), `PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED` (Task 5), `CoinItem` (Task 2).
- 기존 `acquireCoinUseCase` 직접 호출을 `acquirePurchasedCoinUseCase.acquire(userId, item)`로 교체.

- [ ] **Step 1: 의존성·import 교체**

Modify `CompleteCoinPurchaseService.kt`:

기존 import 중 `AcquireCoinUseCase`·`AcquireCoinCommand`·`CoinBalance`·`CoinGetType` 사용부를 다음으로 정리한다. 생성자에서 `acquireCoinUseCase`를 제거하고 두 in-port를 추가한다:

```kotlin
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquirePurchasedCoinUseCase
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinCheckoutUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.IsCoinItemPurchasedUseCase
```

(제거: `AcquireCoinUseCase`, `AcquireCoinCommand`, `CoinGetType` import — 더 이상 이 파일에서 직접 쓰지 않는다.)

생성자 교체:

```kotlin
@Service
class CompleteCoinPurchaseService(
	private val getCoinCheckoutUseCase: GetCoinCheckoutUseCase,
	private val getCoinBalanceUseCase: GetCoinBalanceUseCase,
	private val isCoinItemPurchasedUseCase: IsCoinItemPurchasedUseCase,
	private val paymentGatewayPort: PaymentGatewayPort,
	private val getCoinPaymentPort: GetCoinPaymentPort,
	private val saveCoinPaymentPort: SaveCoinPaymentPort,
	private val updateCoinPaymentStatusPort: UpdateCoinPaymentStatusPort,
	private val acquirePurchasedCoinUseCase: AcquirePurchasedCoinUseCase,
) : CompleteCoinPurchaseUseCase {
```

- [ ] **Step 2: complete 흐름에 선검사·원자 적립 반영**

`complete` 메서드에서 item 조회 직후 선검사를 추가하고, 적립 호출을 교체한다. 아이템 조회 라인 뒤에 삽입:

```kotlin
		// 코인 상품 조회(없으면 COIN-004). salePrice가 서버 확정 실결제가.
		val item: CoinItem = getCoinCheckoutUseCase.getCheckout(command.itemId)

		// 회원당 1회 패키지를 이미 샀으면 PG confirm 전에 막아 헛된 과금을 피한다(이른 409).
		if (item.oncePerUser && isCoinItemPurchasedUseCase.isPurchased(userId, item.id)) {
			throw BusinessException(PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED)
		}
```

기존 적립 블록:

```kotlin
		// ③-성공: 코인을 즉시 적립(원장+잔액 정합)한 뒤 기록을 APPROVED로 전이한다.
		val balance: CoinBalance = acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(amount = item.coinAmount, coinType = CoinGetType.PURCHASE),
		)
		updateCoinPaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.APPROVED)
```

교체:

```kotlin
		// ③-성공: 코인 적립 + (1회 패키지면) 구매 가드를 한 트랜잭션에서 처리한다.
		// 선검사와 적립 사이 경합으로 가드가 먼저 들어갔으면 409로 막히고 적립도 롤백된다.
		val balance: CoinBalance = acquirePurchasedCoinUseCase.acquire(userId, item)
		updateCoinPaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.APPROVED)
```

`replay` 내부의 `getCoinBalanceUseCase`·`CoinBalance` 사용은 그대로 둔다.

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/CompleteCoinPurchaseService.kt
git commit -m "feat(payments): PG 코인 결제완료에 1회 제한 선검사·원자 적립 연결"
```

---

## Task 10: IAP 검증에 SKU 해석·멱등·1회 제한 연결 (core)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/VerifyIapPurchaseService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/IapPurchaseE2ETest.kt` (Task 14에서 작성)

**Interfaces:**
- Consumes: `GetCoinItemBySkuUseCase.getBySku` (Task 6), `IsCoinItemPurchasedUseCase.isPurchased` (Task 6), `AcquirePurchasedCoinUseCase.acquire` (Task 5), `SaveIapPaymentPort`·`GetIapPaymentPort` (Task 8), `CoinSaleChannel` (Task 1), `CoinItem` (Task 2), `PaymentStatus.APPROVED`, `PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED`, `CoinErrorCode.COIN_ITEM_NOT_FOUND`.
- 기존 `coinAmountOf(productId)` 제거.

- [ ] **Step 1: 서비스 전체 교체**

Replace `VerifyIapPurchaseService.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquirePurchasedCoinUseCase
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinItemBySkuUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.IsCoinItemPurchasedUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import com.org.oneulsogae.core.payments.command.application.port.`in`.VerifyIapPurchaseUseCase
import com.org.oneulsogae.core.payments.command.application.port.`in`.command.VerifyIapPurchaseCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.VerifyIapPurchaseResult
import com.org.oneulsogae.core.payments.command.application.port.out.GetIapPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.SaveIapPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.StoreReceiptVerifierPort
import com.org.oneulsogae.core.payments.command.application.port.out.VerifiedReceipt
import com.org.oneulsogae.core.payments.command.domain.IapPayment
import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import org.springframework.stereotype.Service

/**
 * [VerifyIapPurchaseUseCase] 구현. 스토어 인앱결제 영수증을 검증하고 코인을 적립한다.
 * ① SKU→coin_item 해석([GetCoinItemBySkuUseCase]) — IAP 채널 상품인지도 확인.
 * ② transaction_id 멱등([GetIapPaymentPort]) — 이미 처리한 영수증이면 현재 잔액으로 재생(재적립 없음).
 * ③ 1회 패키지 선검사([IsCoinItemPurchasedUseCase]) — 이미 샀으면 409.
 * ④ 영수증 검증([StoreReceiptVerifierPort]) → 적립+가드 원자([AcquirePurchasedCoinUseCase]) → iap_payments 기록.
 */
@Service
class VerifyIapPurchaseService(
	private val getCoinItemBySkuUseCase: GetCoinItemBySkuUseCase,
	private val getIapPaymentPort: GetIapPaymentPort,
	private val isCoinItemPurchasedUseCase: IsCoinItemPurchasedUseCase,
	private val storeReceiptVerifierPort: StoreReceiptVerifierPort,
	private val acquirePurchasedCoinUseCase: AcquirePurchasedCoinUseCase,
	private val saveIapPaymentPort: SaveIapPaymentPort,
	private val getCoinBalanceUseCase: GetCoinBalanceUseCase,
) : VerifyIapPurchaseUseCase {

	override fun verify(userId: Long, command: VerifyIapPurchaseCommand): VerifyIapPurchaseResult {
		// ① SKU → coin_item. IAP로 팔리는 상품이 아니면 거부.
		val item: CoinItem = getCoinItemBySkuUseCase.getBySku(command.productId)
		if (!item.saleChannel.sellableVia(CoinSaleChannel.IAP)) {
			throw BusinessException(PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED.let { PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED }) // placeholder-guard: replaced below
		}

		// ② 멱등: 같은 거래를 이미 처리했으면 재적립 없이 현재 잔액으로 재생한다.
		if (getIapPaymentPort.findByTransactionId(command.transactionId) != null) {
			return VerifyIapPurchaseResult(coinBalance = getCoinBalanceUseCase.getBalance(userId).balance)
		}

		// ③ 1회 패키지 선검사.
		if (item.oncePerUser && isCoinItemPurchasedUseCase.isPurchased(userId, item.id)) {
			throw BusinessException(PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED)
		}

		// ④ 영수증 검증(실패 시 예외).
		val verified: VerifiedReceipt = storeReceiptVerifierPort.verify(
			platform = command.platform,
			productId = command.productId,
			purchaseToken = command.purchaseToken,
			transactionId = command.transactionId,
		)

		// 적립+가드 원자.
		val balance: CoinBalance = acquirePurchasedCoinUseCase.acquire(userId, item)

		// IAP 기록(transaction_id 유니크). 경합으로 이미 있으면 재생 경로와 같은 의미이므로 현재 잔액으로 응답한다.
		saveIapPaymentPort.save(
			IapPayment(
				userId = userId,
				itemId = item.id,
				platform = command.platform,
				productId = verified.productId,
				transactionId = verified.transactionId,
				coinAmount = item.coinAmount,
				status = PaymentStatus.APPROVED,
			),
		)

		return VerifyIapPurchaseResult(coinBalance = balance.balance)
	}
}
```

> 수정 필요: 위 Step에 남긴 `// ① ... 거부` 블록의 예외는 "IAP 상품 아님"을 뜻해야 하는데 `COIN_PACKAGE_ALREADY_PURCHASED`는 의미가 맞지 않는다. 다음 Step에서 전용 에러코드로 교체한다.

- [ ] **Step 2: "IAP 상품 아님" 전용 에러코드 추가·적용**

Modify `PaymentsErrorCode.kt` — `COIN_PACKAGE_ALREADY_PURCHASED` 뒤에 추가:

```kotlin
	/** 회원당 1회 구매 패키지를 이미 구매함. (PG·IAP 공통) */
	COIN_PACKAGE_ALREADY_PURCHASED("PAYMENTS-006", "이미 구매한 패키지입니다.", HttpStatus.CONFLICT),

	/** IAP로 판매하지 않는 상품을 인앱결제로 검증 요청함. */
	COIN_ITEM_NOT_SOLD_VIA_IAP("PAYMENTS-007", "인앱결제로 구매할 수 없는 상품입니다.", HttpStatus.BAD_REQUEST),
}
```

`VerifyIapPurchaseService`의 ① 블록 예외 교체:

```kotlin
		val item: CoinItem = getCoinItemBySkuUseCase.getBySku(command.productId)
		if (!item.saleChannel.sellableVia(CoinSaleChannel.IAP)) {
			throw BusinessException(PaymentsErrorCode.COIN_ITEM_NOT_SOLD_VIA_IAP)
		}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/VerifyIapPurchaseService.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/PaymentsErrorCode.kt
git commit -m "feat(payments): IAP 검증에 SKU 해석·멱등·1회 제한·iap_payments 기록 연결"
```

---

## Task 11: 전체 컴파일·기존 회귀 확인

**Files:** 없음(검증 태스크).

- [ ] **Step 1: 전체 컴파일**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: BUILD SUCCESSFUL. (실패 시 — 특히 `CoinCompleteE2ETest`가 `CoinItemEntity(coinAmount, price, salePrice)`를 직접 생성하는데, 새 필드에 기본값이 있어 3-arg 생성이 계속 유효함을 확인. 컴파일 에러 시 해당 테스트 헬퍼를 `CoinItemEntityFixture.create(...)`로 바꾼다.)

- [ ] **Step 2: 기존 코인·결제 E2E 회귀**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.CoinCompleteE2ETest" --tests "com.org.oneulsogae.api.payments.CoinCheckoutE2ETest" --tests "com.org.oneulsogae.api.coin.*"`
Expected: 기존 통과. (단, 상점 조회 응답 형태가 바뀌었으므로 상점을 직접 검증하는 기존 테스트가 있으면 여기서 드러난다 — 없으면 그대로 통과.)

> 주의: 기존에 `/coins/v1/shop`를 `channel` 없이 호출하는 E2E가 있으면 400이 된다(필수 파라미터). 검색: `grep -rn '/coins/v1/shop' oneulsogae-api/src/test`. 있으면 해당 테스트에 `?channel=PG`를 붙여 갱신하고 이 태스크에 포함해 커밋한다. (현재 상점 전용 E2E는 없음 — Task 12에서 신규 작성)

- [ ] **Step 3: Commit (회귀 수정이 있었을 때만)**

```bash
git add -A
git commit -m "test(coin): 상점 조회 시그니처 변경에 따른 기존 테스트 정합"
```

---

## Task 12: 상점 채널 필터·1회 패키지 숨김 E2E

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/coin/CoinShopE2ETest.kt`

**Interfaces:**
- Consumes: `GET /coins/v1/shop?channel=`, `CoinItemEntityFixture` (Task 3), `CoinItemPurchaseEntity` (Task 4), `IntegrationUtil`, `AbstractIntegrationSupport`.

- [ ] **Step 1: E2E 테스트 작성**

Create `CoinShopE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.coin

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.coin.command.entity.CoinItemPurchaseEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not

/**
 * `GET /coins/v1/shop?channel=` E2E 테스트.
 * - 채널 필터: IAP 요청 시 PG 전용 상품 제외, IAP·BOTH 포함.
 * - 1회 패키지: 이미 구매한 once_per_user 상품은 목록에서 숨김.
 * 상점 조회는 order-by가 없어 순서 대신 집합 멤버십(hasItem)으로 단언한다.
 * (id는 RestAssured가 Int로 역직렬화하므로 matcher에 Int로 넣는다)
 */
class CoinShopE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QCoinItemPurchaseEntity.coinItemPurchaseEntity)
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
	}

	describe("GET /coins/v1/shop") {

		context("channel=IAP로 조회하면") {
			it("PG 전용 상품은 빠지고 IAP·BOTH 상품만 내려간다") {
				val userId = 9101L
				IntegrationUtil.persist(CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG))
				val iapId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.300"),
				).id!!
				val bothId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 500, saleChannel = CoinSaleChannel.BOTH, storeProductId = "sku.both.500"),
				).id!!

				val pgOnlyId = IntegrationUtil.persist(CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG)).id!!

				get("/coins/v1/shop?channel=IAP") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 2)
					body("data.id", hasItem(iapId.toInt()))
					body("data.id", hasItem(bothId.toInt()))
					body("data.id", not(hasItem(pgOnlyId.toInt())))
				}
			}
		}

		context("channel=PG로 조회하면") {
			it("IAP 전용 상품은 빠지고 PG·BOTH 상품만 내려간다") {
				val userId = 9102L
				val pgId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG),
				).id!!
				val iapOnlyId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.301"),
				).id!!
				val bothId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 500, saleChannel = CoinSaleChannel.BOTH, storeProductId = "sku.both.501"),
				).id!!

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 2)
					body("data.id", hasItem(pgId.toInt()))
					body("data.id", hasItem(bothId.toInt()))
					body("data.id", not(hasItem(iapOnlyId.toInt())))
				}
			}
		}

		context("이미 구매한 1회 패키지가 있으면") {
			it("그 사용자에게는 목록에서 숨겨진다 (일반 상품은 유지)") {
				val userId = 9103L
				val normalId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG),
				).id!!
				val packageId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, oncePerUser = true, saleChannel = CoinSaleChannel.PG),
				).id!!
				// 이 사용자가 패키지를 이미 구매한 상태로 만든다.
				IntegrationUtil.persist(CoinItemPurchaseEntity(userId = userId, itemId = packageId))

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data.id", hasItem(normalId.toInt()))
					body("data.id", not(hasItem(packageId.toInt())))
				}
			}
		}

		context("1회 패키지를 아직 안 산 다른 사용자면") {
			it("패키지가 목록에 보이고 oncePerUser=true다") {
				val buyerId = 9104L
				val otherId = 9105L
				val packageId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, oncePerUser = true, saleChannel = CoinSaleChannel.PG),
				).id!!
				IntegrationUtil.persist(CoinItemPurchaseEntity(userId = buyerId, itemId = packageId))

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(otherId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data[0].id", packageId.toInt())
					body("data[0].oncePerUser", true)
				}
			}
		}
	}
})
```

주의: `get`/`bearer`/`expect`/`status`/`body(path, Any?)`/`body(path, Matcher)`는 기존 `RestAssuredDsl`에 모두 존재한다(확인 완료). 집합 멤버십은 Hamcrest `hasItem`/`not(hasItem(...))` matcher 오버로드(`body(path, matcher)`)로 단언해 조회 순서에 의존하지 않는다.

- [ ] **Step 2: 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.coin.CoinShopE2ETest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/coin/CoinShopE2ETest.kt
git commit -m "test(coin): 상점 채널 필터·1회 패키지 숨김 E2E 추가"
```

---

## Task 13: PG 1회 패키지 구매 제한 E2E

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/CoinCompleteOncePackageE2ETest.kt`

**Interfaces:**
- Consumes: `POST /payments/v1/coin/complete`, `CoinItemEntityFixture` (once_per_user), `coin_item_purchases` 검증.

- [ ] **Step 1: E2E 작성**

Create `CoinCompleteOncePackageE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.payments

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.payments.command.entity.QCoinPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * PG 코인 결제완료의 1회 패키지 제한 E2E.
 * - once_per_user 상품 최초 구매: 적립 + coin_item_purchases 가드 기록.
 * - 2회차: 409(PAYMENTS-006), 재적립 없음.
 */
class CoinCompleteOncePackageE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QCoinItemPurchaseEntity.coinItemPurchaseEntity)
		IntegrationUtil.deleteAll(QCoinPaymentEntity.coinPaymentEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
	}

	fun purchaseGuardCount(userId: Long, itemId: Long): Int {
		val p = QCoinItemPurchaseEntity.coinItemPurchaseEntity
		return IntegrationUtil.getQuery().selectFrom(p)
			.where(p.userId.eq(userId), p.itemId.eq(itemId)).fetch().size
	}

	fun balanceOf(userId: Long): Int? {
		val b = QCoinBalanceEntity.coinBalanceEntity
		return IntegrationUtil.getQuery().selectFrom(b).where(b.userId.eq(userId)).fetchOne()?.balance
	}

	describe("POST /payments/v1/coin/complete (1회 패키지)") {

		context("once_per_user 패키지를 처음 구매하면") {
			it("적립하고 구매 가드를 남긴다") {
				val userId = 9201L
				val itemId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, price = 30000, salePrice = 19900, oncePerUser = true),
				).id!!

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "pkg_key_1", "orderId": "ord_pkg_1"}""")
				} expect {
					status(200)
					body("data.coinAmount", 300)
					body("data.balance", 300)
				}

				purchaseGuardCount(userId, itemId) shouldBe 1
				balanceOf(userId) shouldBe 300
			}
		}

		context("이미 구매한 패키지를 다시 구매하면") {
			it("409(PAYMENTS-006)이고 재적립되지 않는다") {
				val userId = 9202L
				val itemId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, price = 30000, salePrice = 19900, oncePerUser = true),
				).id!!

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "pkg_key_2", "orderId": "ord_pkg_2"}""")
				} expect { status(200) }

				// 2회차: 다른 paymentKey(새 PG 인증)로 재시도 → 선검사에서 막힌다.
				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "pkg_key_2b", "orderId": "ord_pkg_2b"}""")
				} expect {
					status(409)
					body("error.code", "PAYMENTS-006")
				}

				purchaseGuardCount(userId, itemId) shouldBe 1
				balanceOf(userId) shouldBe 300
			}
		}
	}
})
```

- [ ] **Step 2: 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.CoinCompleteOncePackageE2ETest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/CoinCompleteOncePackageE2ETest.kt
git commit -m "test(payments): PG 1회 패키지 구매 제한 E2E 추가"
```

---

## Task 14: IAP SKU 해석·멱등·1회 제한 E2E

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/IapPurchaseE2ETest.kt`

**Interfaces:**
- Consumes: `POST /coins/v1/iap/purchases`, `StubStoreReceiptVerifierAdapter`(운영 스텁이 토큰만 확인·통과 — 별도 페이크 불필요), `iap_payments`·`coin_item_purchases` 검증.

- [ ] **Step 1: E2E 작성**

Create `IapPurchaseE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.payments

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.payments.command.entity.QIapPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /coins/v1/iap/purchases` E2E.
 * - SKU→coin_item 해석 후 적립 + iap_payments 기록.
 * - 같은 transaction_id 재검증: 재적립 없이 현재 잔액 재생.
 * - 1회 패키지: 다른 거래로 같은 상품 재구매 시 409.
 * - IAP 미판매(PG 전용) 상품 SKU: 400(PAYMENTS-007). (스텁 검증기가 토큰만 확인·통과하므로 채널 판정만 검증됨)
 */
class IapPurchaseE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QIapPaymentEntity.iapPaymentEntity)
		IntegrationUtil.deleteAll(QCoinItemPurchaseEntity.coinItemPurchaseEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
	}

	fun balanceOf(userId: Long): Int? {
		val b = QCoinBalanceEntity.coinBalanceEntity
		return IntegrationUtil.getQuery().selectFrom(b).where(b.userId.eq(userId)).fetchOne()?.balance
	}

	fun iapCount(txId: String): Int {
		val p = QIapPaymentEntity.iapPaymentEntity
		return IntegrationUtil.getQuery().selectFrom(p).where(p.transactionId.eq(txId)).fetch().size
	}

	describe("POST /coins/v1/iap/purchases") {

		context("IAP 상품 영수증을 검증하면") {
			it("SKU로 상품을 찾아 적립하고 iap_payments를 남긴다") {
				val userId = 9301L
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.a"),
				)

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.iap.a","purchaseToken":"tok-1","transactionId":"txn-1"}""")
				} expect {
					status(200)
					body("data.coinBalance", 300)
				}

				balanceOf(userId) shouldBe 300
				iapCount("txn-1") shouldBe 1
			}
		}

		context("같은 transactionId로 다시 검증하면") {
			it("재적립 없이 현재 잔액을 재생한다") {
				val userId = 9302L
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.b"),
				)
				val body = """{"platform":"IOS","productId":"sku.iap.b","purchaseToken":"tok-2","transactionId":"txn-2"}"""

				post("/coins/v1/iap/purchases") { bearer(accessTokenFor(userId)); jsonBody(body) } expect { status(200) }
				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId)); jsonBody(body)
				} expect {
					status(200)
					body("data.coinBalance", 300)
				}

				balanceOf(userId) shouldBe 300
				iapCount("txn-2") shouldBe 1
			}
		}

		context("1회 패키지를 다른 거래로 재구매하면") {
			it("409(PAYMENTS-006)") {
				val userId = 9303L
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(
						coinAmount = 300, oncePerUser = true, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.pkg",
					),
				)

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.iap.pkg","purchaseToken":"tok-3","transactionId":"txn-3a"}""")
				} expect { status(200) }

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.iap.pkg","purchaseToken":"tok-3","transactionId":"txn-3b"}""")
				} expect {
					status(409)
					body("error.code", "PAYMENTS-006")
				}

				balanceOf(userId) shouldBe 300
			}
		}

		context("IAP로 팔지 않는 PG 전용 상품 SKU면") {
			it("400(PAYMENTS-007)") {
				val userId = 9304L
				// PG 전용이지만 SKU를 붙여 저장(해석은 되나 채널 판정에서 거부되는지 검증).
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG, storeProductId = "sku.pgonly"),
				)

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.pgonly","purchaseToken":"tok-4","transactionId":"txn-4"}""")
				} expect {
					status(400)
					body("error.code", "PAYMENTS-007")
				}
			}
		}
	}
})
```

주의: PG 전용 상품에 SKU를 붙이는 건 도메인 `CoinItem.create` 불변식엔 안 걸린다(불변식은 IAP/BOTH일 때만 SKU 필수 — PG에 SKU 있어도 허용). 픽스처는 엔티티를 직접 만들므로 검증을 우회한다. 이 케이스는 "SKU 해석은 되지만 채널이 PG라 IAP 거부" 경로를 검증하는 의도다.

- [ ] **Step 2: 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.IapPurchaseE2ETest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/IapPurchaseE2ETest.kt
git commit -m "test(payments): IAP SKU 해석·멱등·1회 제한 E2E 추가"
```

---

## Task 15: 전체 테스트·마이그레이션 문서

**Files:**
- Create: `docs/migration/` 하위 마이그레이션 SQL 문서(프로젝트 관례 확인 후 위치 결정).

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 마이그레이션 SQL 문서화**

`docs/migration/` 관례를 확인(`ls docs/migration`)하고 해당 형식으로 아래 DDL을 남긴다. 파일이 관례상 없으면 스펙 문서 하단에 이미 기재된 DDL로 갈음하고 이 스텝을 건너뛴다.

```sql
-- coin_items: 판매채널·1회제한·SKU
ALTER TABLE coin_items
  ADD COLUMN once_per_user TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN sale_channel VARCHAR(10) NOT NULL DEFAULT 'PG',
  ADD COLUMN store_product_id VARCHAR(255) NULL,
  ADD UNIQUE KEY ux_coin_items_store_product_id (store_product_id);

-- 1회 구매 가드
CREATE TABLE coin_item_purchases (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_coin_item_purchases_user_item (user_id, item_id)
);

-- IAP 결제 기록
CREATE TABLE iap_payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  platform VARCHAR(10) NOT NULL,
  product_id VARCHAR(255) NOT NULL,
  transaction_id VARCHAR(255) NOT NULL,
  coin_amount INT NOT NULL,
  status VARCHAR(50) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_iap_payments_transaction_id (transaction_id),
  KEY idx_user_id (user_id)
);
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs(coin): 1회 패키지·판매채널·IAP 결제 마이그레이션 DDL 문서화"
```

---

## Self-Review

**Spec coverage:**
- coin_items 3컬럼(once_per_user·sale_channel·store_product_id) → Task 2·3. ✓
- coin_item_purchases 가드(UNIQUE) → Task 4. ✓
- iap_payments(UNIQUE txn) → Task 8. ✓
- 적립+가드 원자(AcquirePurchasedCoinUseCase) → Task 5. ✓
- 상점 채널 필터 + 1회 숨김(?channel=) → Task 6·7·12. ✓
- IAP SKU 해석·멱등·채널 판정 → Task 6(SKU port)·8·10·14. ✓
- 에러코드 PAYMENTS-006(+007 IAP 채널) → Task 5·10. ✓
- PG 1회 제한 연결 → Task 9·13. ✓
- 도메인 불변식(IAP/BOTH ⟹ SKU) → Task 2. ✓
- 마이그레이션 DDL → Task 15. ✓

**타입 정합:** `getCoinShop(userId: Long, channel: CoinSaleChannel)`, `findShopItems(userId, channel)`, `getBySku(sku): CoinItem`, `isPurchased(userId, itemId): Boolean`, `acquire(userId, item: CoinItem): CoinBalance`, `save(userId, itemId)`(가드)·`save(iapPayment): IapPayment`(IAP) — Task 정의와 사용처 일치. `CoinItem` 6-arg 투영(id 포함 7개 값 → 생성자 파라미터 id·coinAmount·price·salePrice·oncePerUser·saleChannel·storeProductId 7개)와 Projections 인자 수 일치.

**주의(플랜 갭 방지):**
- Task 6에서 `findAll()` 제거 → 기존 참조는 `GetCoinShopService`뿐(Task 6에서 함께 교체). 다른 참조 없음(구현 전 `grep -rn "findAll" oneulsogae-*/src/main/kotlin | grep CoinItem`로 확인).
- Task 10 Step 1의 ① 블록에 placeholder식 표현이 있으나 Step 2에서 전용 에러코드로 교체하도록 명시 — 실행 시 Step 1·2를 한 커밋으로 묶어 최종 코드만 남긴다.
- `get`/`post`/`expect`/`bearer`/`jsonBody`/`body(path, List)` DSL은 기존 E2E와 동일. 배열 매칭 미지원 시 size+인덱스 검증으로 대체(Task 12 주석).

## Execution Handoff

계획을 `docs/superpowers/plans/2026-07-25-once-per-user-coin-package.md`에 저장했다.
