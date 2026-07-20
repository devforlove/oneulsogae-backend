# gathering_products 도입 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 일정(gathering_schedules)의 가격 컬럼을 성별·티어별 상품 행(gathering_products)으로 이관하고, 일정 생성 시 products가 함께 생성되게 한다.

**Architecture:** 확장(expand) → 전환(cutover) → 수축(contract) 순서로 진행한다. Task 1~3에서 products를 기존 컬럼과 병행 생성하고, Task 4~6에서 참가·조회 경로를 products 기반으로 전환한 뒤, Task 7에서 스케줄의 가격 컬럼·필드를 제거한다. 각 Task 종료 시점에 전체가 컴파일되고 테스트가 통과한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4.0.6 / Spring Data JPA + QueryDSL / Kotest / RestAssured E2E(Testcontainers)

**Spec:** `docs/superpowers/specs/2026-07-16-gathering-products-design.md`

## Global Constraints

- 응답·주석·커밋 메시지는 한국어. 커밋 형식 `<type>(gathering): <설명>`.
- 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다.
- 들여쓰기는 탭(기존 코드와 동일).
- Controller는 in-port UseCase만 주입, Service가 UseCase 구현, infra Adapter가 out-port 구현(`toDomain()`/`toEntity()`).
- 엔티티마다 어댑터 하나. 조회 dao는 `query`의 `*DaoImpl`(JPAQueryFactory만 주입).
- E2E는 `AbstractIntegrationSupport` 상속 + `IntegrationUtil`/엔티티 픽스처 사용, 리포지토리 직접 의존 금지.
- 유닛 테스트 실행: `./gradlew :oneulsogae-api:test --tests "<FQCN 또는 패턴>"`. E2E도 동일 모듈.
- 운영 DDL은 이번 작업에서 적용하지 않는다(스펙 문서 8절에 기록됨).

---

### Task 1: GatheringProductType(common) + admin 도메인 GatheringProduct·GatheringProducts

**Files:**
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/gathering/GatheringProductType.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringProduct.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringProducts.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringProductsTest.kt`

**Interfaces:**
- Consumes: `GatheringFee(male: Int, female: Int)` (admin 도메인, 기존), `Gender` (`com.org.oneulsogae.common.user.Gender`, MALE/FEMALE)
- Produces: `GatheringProductType` enum(NORMAL/EARLY_BIRD/DISCOUNT), `GatheringProduct(id, gatheringId, scheduleId, gender, type, price)` data class, `GatheringProducts(values: List<GatheringProduct>)` + `GatheringProducts.create(gatheringId, fee, earlyBirdDiscountRate, discountFee): GatheringProducts` + `withScheduleId(scheduleId: Long): GatheringProducts`

- [ ] **Step 1: 실패하는 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringProductsTest.kt`:

```kotlin
package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.admin.gathering.command.domain.GatheringFee
import com.org.oneulsogae.admin.gathering.command.domain.GatheringProduct
import com.org.oneulsogae.admin.gathering.command.domain.GatheringProducts
import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class GatheringProductsTest : DescribeSpec({

	val fee: GatheringFee = GatheringFee(male = 10000, female = 8000)

	describe("GatheringProducts.create") {

		it("정상가만 있으면 남/녀 NORMAL 2행을 만든다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = null,
				discountFee = null,
			)

			products.values.map { product: GatheringProduct -> Triple(product.gender, product.type, product.price) }
				.shouldContainExactlyInAnyOrder(
					Triple(Gender.MALE, GatheringProductType.NORMAL, 10000),
					Triple(Gender.FEMALE, GatheringProductType.NORMAL, 8000),
				)
		}

		it("얼리버드 할인율이 있으면 정가 × (100 - 율) / 100 버림 가격의 EARLY_BIRD 행을 추가한다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = 30,
				discountFee = null,
			)

			products.values.map { product: GatheringProduct -> Triple(product.gender, product.type, product.price) }
				.shouldContainExactlyInAnyOrder(
					Triple(Gender.MALE, GatheringProductType.NORMAL, 10000),
					Triple(Gender.FEMALE, GatheringProductType.NORMAL, 8000),
					Triple(Gender.MALE, GatheringProductType.EARLY_BIRD, 7000),
					Triple(Gender.FEMALE, GatheringProductType.EARLY_BIRD, 5600),
				)
		}

		it("얼리버드 가격은 버림으로 계산한다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = GatheringFee(male = 9999, female = 9999),
				earlyBirdDiscountRate = 13,
				discountFee = null,
			)

			// 9999 × 87 / 100 = 8699.13 → 8699
			products.values.first { product: GatheringProduct -> product.type == GatheringProductType.EARLY_BIRD }
				.price shouldBe 8699
		}

		it("할인가가 있으면 남/녀 DISCOUNT 행을 추가한다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = 30,
				discountFee = GatheringFee(male = 9000, female = 7000),
			)

			products.values.size shouldBe 6
			products.values.filter { product: GatheringProduct -> product.type == GatheringProductType.DISCOUNT }
				.map { product: GatheringProduct -> product.gender to product.price }
				.shouldContainExactlyInAnyOrder(
					Gender.MALE to 9000,
					Gender.FEMALE to 7000,
				)
		}
	}

	describe("withScheduleId") {

		it("모든 행에 scheduleId를 채운 새 컬렉션을 돌려준다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = null,
				discountFee = null,
			)

			val assigned: GatheringProducts = products.withScheduleId(77L)

			assigned.values.all { product: GatheringProduct -> product.scheduleId == 77L } shouldBe true
			// 원본은 변경되지 않는다.
			products.values.all { product: GatheringProduct -> product.scheduleId == 0L } shouldBe true
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.GatheringProductsTest"`
Expected: 컴파일 실패 (`GatheringProduct`/`GatheringProducts`/`GatheringProductType` unresolved)

- [ ] **Step 3: 구현**

`oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/gathering/GatheringProductType.kt`:

```kotlin
package com.org.oneulsogae.common.gathering

/** 모임 일정 상품([com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity])의 가격 타입. */
enum class GatheringProductType(val description: String) {

	/** 정가. 모든 일정이 남/녀 각 1행씩 가진다. */
	NORMAL("정가"),

	/** 얼리버드가. 얼리버드 선착순(스케줄의 early_bird_remaining)이 남아있을 때 적용된다. */
	EARLY_BIRD("얼리버드가"),

	/** 할인가. 얼리버드 소진 후 적용된다. */
	DISCOUNT("할인가"),
}
```

`oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringProduct.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender

/**
 * 일정의 성별·티어별 가격 한 건(상품). 한 행 = 한 가격([gender] × [type])이며,
 * 얼리버드가도 조회 시점 계산 없이 생성 시점에 확정된 금액([price])으로 저장한다.
 * 일정 저장 전에는 [scheduleId]가 0이고, 저장 후 [GatheringProducts.withScheduleId]로 채운다.
 */
data class GatheringProduct(
	val id: Long = 0,
	val gatheringId: Long,
	val scheduleId: Long = 0,
	val gender: Gender,
	val type: GatheringProductType,
	val price: Int,
)
```

`oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringProducts.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender

/**
 * 한 일정의 상품([GatheringProduct]) 일급 컬렉션.
 * 티어 구성 규칙을 캡슐화한다: 남/녀 × NORMAL은 필수, EARLY_BIRD는 할인율이 있을 때(가격 = 정가 × (100 - 율) / 100 버림),
 * DISCOUNT는 할인가가 있을 때 남/녀 행을 만든다.
 */
data class GatheringProducts(
	val values: List<GatheringProduct>,
) {

	/** 일정 저장 후 발급된 [scheduleId]를 모든 행에 채운 새 컬렉션을 돌려준다. */
	fun withScheduleId(scheduleId: Long): GatheringProducts =
		GatheringProducts(values.map { product: GatheringProduct -> product.copy(scheduleId = scheduleId) })

	companion object {

		/**
		 * [gatheringId] 모임 일정의 상품 목록을 만든다. 입력 검증(할인율 범위·세트 규칙)은
		 * [GatheringSchedule.create]가 먼저 수행하므로 여기서는 구성만 담당한다.
		 */
		fun create(
			gatheringId: Long,
			fee: GatheringFee,
			earlyBirdDiscountRate: Int?,
			discountFee: GatheringFee?,
		): GatheringProducts {
			val products: MutableList<GatheringProduct> = mutableListOf(
				product(gatheringId, Gender.MALE, GatheringProductType.NORMAL, fee.male),
				product(gatheringId, Gender.FEMALE, GatheringProductType.NORMAL, fee.female),
			)
			if (earlyBirdDiscountRate != null) {
				products += product(gatheringId, Gender.MALE, GatheringProductType.EARLY_BIRD, earlyBirdPrice(fee.male, earlyBirdDiscountRate))
				products += product(gatheringId, Gender.FEMALE, GatheringProductType.EARLY_BIRD, earlyBirdPrice(fee.female, earlyBirdDiscountRate))
			}
			if (discountFee != null) {
				products += product(gatheringId, Gender.MALE, GatheringProductType.DISCOUNT, discountFee.male)
				products += product(gatheringId, Gender.FEMALE, GatheringProductType.DISCOUNT, discountFee.female)
			}
			return GatheringProducts(products)
		}

		// 얼리버드가 = 정가 × (100 - 할인율) / 100 (정수 나눗셈 버림).
		private fun earlyBirdPrice(fee: Int, discountRate: Int): Int =
			fee * (100 - discountRate) / 100

		private fun product(gatheringId: Long, gender: Gender, type: GatheringProductType, price: Int): GatheringProduct =
			GatheringProduct(gatheringId = gatheringId, gender = gender, type = type, price = price)
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.GatheringProductsTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/gathering/GatheringProductType.kt \
  oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringProduct.kt \
  oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringProducts.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringProductsTest.kt
git commit -m "feat(gathering): 일정 상품 도메인 GatheringProducts 추가"
```

---

### Task 2: infra GatheringProductEntity + 리포지토리 + 매퍼 + 어댑터 + 픽스처

**Files:**
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/entity/GatheringProductEntity.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/repository/GatheringProductJpaRepository.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/mapper/GatheringProductMapper.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/out/SaveGatheringProductPort.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringProductAdapter.kt`
- Create: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/GatheringProductEntityFixture.kt`

**Interfaces:**
- Consumes: Task 1의 `GatheringProduct`/`GatheringProducts`/`GatheringProductType`, `BaseEntity`(infra common)
- Produces:
  - `GatheringProductEntity(gatheringId, scheduleId, gender, type, price)` — 테이블 `gathering_products`
  - `GatheringProductJpaRepository.findByScheduleId(scheduleId: Long): List<GatheringProductEntity>`
  - `SaveGatheringProductPort.saveAll(products: GatheringProducts)` (admin out-port, `GatheringProductAdapter`가 구현)
  - `GatheringProductEntityFixture.create(...)` + `GatheringProductEntityFixture.tierSet(...)`(E2E용 티어 세트 생성)

- [ ] **Step 1: 엔티티 작성**

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/entity/GatheringProductEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.gathering.command.entity

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 한 일정([GatheringScheduleEntity])의 성별·티어별 가격 한 건(상품)을 담는 영속성 엔티티.
 * 한 일정이 성별(2) × 타입(1~3) 조합으로 2~6행을 가진다. 소속 일정은 [scheduleId], 모임은 [gatheringId]로 참조한다.
 * 얼리버드가도 할인율이 아니라 생성 시점에 계산된 확정 금액([price])으로 저장한다.
 * 얼리버드 선착순 수량은 남/녀 공유 카운터라 일정 테이블(early_bird_capacity/remaining)에 남아있다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gathering_products",
	indexes = [
		// 일정별 상품 조회용 인덱스. (schedule_id 동등/IN 조건)
		Index(name = "idx_schedule_id", columnList = "schedule_id"),
	],
)
class GatheringProductEntity(
	/** 소속 모임 id. */
	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	/** 소속 일정 id. */
	@Column(name = "schedule_id", nullable = false)
	val scheduleId: Long,

	/** 가격 적용 성별. */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	val gender: Gender,

	/** 가격 타입(정가·얼리버드가·할인가). */
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	val type: GatheringProductType,

	/** 확정 금액(원). 0이면 무료. */
	@Column(name = "price", nullable = false)
	val price: Int,
) : BaseEntity()
```

- [ ] **Step 2: 리포지토리·매퍼·포트·어댑터 작성**

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/repository/GatheringProductJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.gathering.command.repository

import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringProductJpaRepository : JpaRepository<GatheringProductEntity, Long> {

	/** 한 일정의 상품 전부를 조회한다. (성별 2 × 타입 1~3 = 2~6행) */
	fun findByScheduleId(scheduleId: Long): List<GatheringProductEntity>
}
```

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/mapper/GatheringProductMapper.kt`:

```kotlin
package com.org.oneulsogae.infra.gathering.command.mapper

import com.org.oneulsogae.admin.gathering.command.domain.GatheringProduct
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity

fun GatheringProductEntity.toDomain(): GatheringProduct =
	GatheringProduct(
		id = id ?: 0,
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		gender = gender,
		type = type,
		price = price,
	)

fun GatheringProduct.toEntity(): GatheringProductEntity =
	GatheringProductEntity(
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		gender = gender,
		type = type,
		price = price,
	).also { if (id != 0L) it.id = id }
```

`oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/out/SaveGatheringProductPort.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.admin.gathering.command.domain.GatheringProducts

/** 일정 상품 일괄 저장 out-port. infra 어댑터가 구현한다. */
fun interface SaveGatheringProductPort {

	fun saveAll(products: GatheringProducts)
}
```

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringProductAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.gathering.command.adapter

import com.org.oneulsogae.admin.gathering.command.application.port.out.SaveGatheringProductPort
import com.org.oneulsogae.admin.gathering.command.domain.GatheringProduct
import com.org.oneulsogae.admin.gathering.command.domain.GatheringProducts
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.mapper.toEntity
import com.org.oneulsogae.infra.gathering.command.repository.GatheringProductJpaRepository
import org.springframework.stereotype.Component

/**
 * [GatheringProductEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 어드민 일정 생성의 상품 일괄 저장([SaveGatheringProductPort]) out-port를 구현한다.
 */
@Component
class GatheringProductAdapter(
	private val gatheringProductJpaRepository: GatheringProductJpaRepository,
) : SaveGatheringProductPort {

	override fun saveAll(products: GatheringProducts) {
		val entities: List<GatheringProductEntity> =
			products.values.map { product: GatheringProduct -> product.toEntity() }
		gatheringProductJpaRepository.saveAll(entities)
	}
}
```

- [ ] **Step 3: 테스트 픽스처 작성**

`oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/GatheringProductEntityFixture.kt`:

```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity

/**
 * [GatheringProductEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * (created_at은 저장 시 JPA Auditing이 채운다)
 */
object GatheringProductEntityFixture {

	fun create(
		gatheringId: Long = 1L,
		scheduleId: Long = 1L,
		gender: Gender = Gender.MALE,
		type: GatheringProductType = GatheringProductType.NORMAL,
		price: Int = 10000,
	): GatheringProductEntity =
		GatheringProductEntity(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			gender = gender,
			type = type,
			price = price,
		)

	/**
	 * 한 일정의 티어 세트를 만든다. 정가(남/녀)는 필수, 얼리버드 할인율·할인가는 선택.
	 * 얼리버드가는 정가 × (100 - 율) / 100 버림으로 계산한다. (도메인 생성 규칙과 동일)
	 */
	fun tierSet(
		gatheringId: Long,
		scheduleId: Long,
		maleFee: Int = 10000,
		femaleFee: Int = 8000,
		earlyBirdDiscountRate: Int? = null,
		discountMaleFee: Int? = null,
		discountFemaleFee: Int? = null,
	): List<GatheringProductEntity> {
		val products: MutableList<GatheringProductEntity> = mutableListOf(
			create(gatheringId, scheduleId, Gender.MALE, GatheringProductType.NORMAL, maleFee),
			create(gatheringId, scheduleId, Gender.FEMALE, GatheringProductType.NORMAL, femaleFee),
		)
		if (earlyBirdDiscountRate != null) {
			products += create(gatheringId, scheduleId, Gender.MALE, GatheringProductType.EARLY_BIRD, maleFee * (100 - earlyBirdDiscountRate) / 100)
			products += create(gatheringId, scheduleId, Gender.FEMALE, GatheringProductType.EARLY_BIRD, femaleFee * (100 - earlyBirdDiscountRate) / 100)
		}
		if (discountMaleFee != null) {
			products += create(gatheringId, scheduleId, Gender.MALE, GatheringProductType.DISCOUNT, discountMaleFee)
		}
		if (discountFemaleFee != null) {
			products += create(gatheringId, scheduleId, Gender.FEMALE, GatheringProductType.DISCOUNT, discountFemaleFee)
		}
		return products
	}
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-infra:compileTestFixturesKotlin :oneulsogae-admin:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/entity/GatheringProductEntity.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/repository/GatheringProductJpaRepository.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/mapper/GatheringProductMapper.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringProductAdapter.kt \
  oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/out/SaveGatheringProductPort.kt \
  oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/GatheringProductEntityFixture.kt
git commit -m "feat(gathering): gathering_products 엔티티·어댑터·픽스처 추가"
```

---

### Task 3: 일정 생성 시 products 함께 생성 (확장 단계 — 기존 컬럼 병행 유지)

**Files:**
- Modify: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringSchedule.kt`
- Modify: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/CreateGatheringScheduleService.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringScheduleTest.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminGatheringScheduleE2ETest.kt`

**Interfaces:**
- Consumes: Task 1 `GatheringProducts.create(...)`, Task 2 `SaveGatheringProductPort.saveAll(products)`
- Produces: `GatheringSchedule.products: GatheringProducts`(기본값 빈 컬렉션 — 로드 경로는 products를 싣지 않음). `GatheringSchedule.create()`는 기존 검증 후 products를 함께 구성. **이 Task에서 기존 fee 필드·엔티티 컬럼은 그대로 둔다(Task 7에서 제거).**

- [ ] **Step 1: 유닛 테스트 추가 (실패 확인)**

`GatheringScheduleTest.kt`의 `describe("GatheringSchedule.create")` 블록 안, 첫 번째 `it` 아래에 추가:

```kotlin
		it("생성 시 성별·티어별 products를 함께 구성한다") {
			val schedule: GatheringSchedule = GatheringSchedule.create(
				gatheringId = 1L,
				startAt = start,
				endAt = end,
				fee = fee,
				earlyBirdDiscountRate = 30,
				earlyBirdCapacity = 2,
				discountFee = GatheringFee(male = 9000, female = 7000),
				maxParticipants = maxParticipants,
				now = now,
			)

			// 남/녀 × (NORMAL, EARLY_BIRD, DISCOUNT) = 6행.
			schedule.products.values.size shouldBe 6
			schedule.products.values
				.first { product: GatheringProduct -> product.gender == Gender.FEMALE && product.type == GatheringProductType.EARLY_BIRD }
				.price shouldBe 5600 // 8000 × 70%
		}

		it("얼리버드·할인가가 없으면 products는 남/녀 NORMAL 2행이다") {
			val schedule: GatheringSchedule = GatheringSchedule.create(
				gatheringId = 1L,
				startAt = start,
				endAt = null,
				fee = fee,
				earlyBirdDiscountRate = null,
				earlyBirdCapacity = null,
				discountFee = null,
				maxParticipants = maxParticipants,
				now = now,
			)

			schedule.products.values.map { product: GatheringProduct -> product.type }
				.all { type: GatheringProductType -> type == GatheringProductType.NORMAL } shouldBe true
			schedule.products.values.size shouldBe 2
		}
```

파일 상단 import 추가:

```kotlin
import com.org.oneulsogae.admin.gathering.command.domain.GatheringProduct
import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.GatheringScheduleTest"`
Expected: 컴파일 실패 (`schedule.products` unresolved)

- [ ] **Step 3: 도메인·서비스 구현**

`GatheringSchedule.kt` — data class에 products 프로퍼티 추가(마지막 파라미터, 기본값 빈 컬렉션):

```kotlin
	// 생성 직후는 예정(SCHEDULED). 시작/종료/취소로 전이한다.
	val status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
	// 성별·티어별 가격 상품. create()가 구성하며, 저장소에서 로드된 일정은 빈 컬렉션이다(상태 전이 등 products 불필요 경로).
	val products: GatheringProducts = GatheringProducts(emptyList()),
) {
```

`create()`의 반환 직전 `GatheringSchedule(...)` 생성에 products 추가:

```kotlin
			return GatheringSchedule(
				gatheringId = gatheringId,
				startAt = startAt,
				endAt = endAt,
				fee = fee,
				maleCapacity = genderCapacity,
				femaleCapacity = genderCapacity,
				earlyBirdDiscountRate = earlyBirdDiscountRate,
				earlyBirdCapacity = earlyBirdCapacity,
				discountFee = discountFee,
				products = GatheringProducts.create(
					gatheringId = gatheringId,
					fee = fee,
					earlyBirdDiscountRate = earlyBirdDiscountRate,
					discountFee = discountFee,
				),
			)
```

KDoc의 create 설명에 한 줄 추가: `생성 시 성별·티어별 가격 상품([GatheringProducts])을 함께 구성한다.`

`CreateGatheringScheduleService.kt` — 포트 주입 및 저장:

```kotlin
@Service
@Transactional
class CreateGatheringScheduleService(
	private val loadAdminGatheringPort: LoadAdminGatheringPort,
	private val saveGatheringSchedulePort: SaveGatheringSchedulePort,
	private val saveGatheringProductPort: SaveGatheringProductPort,
	private val timeGenerator: TimeGenerator,
) : CreateGatheringScheduleUseCase {

	override fun create(command: CreateGatheringScheduleCommand): GatheringSchedule {
		val gathering: AdminGathering = loadAdminGatheringPort.loadById(command.gatheringId)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: ${command.gatheringId}")
		val schedule: GatheringSchedule = GatheringSchedule.create(
			gatheringId = command.gatheringId,
			startAt = command.startAt,
			endAt = command.endAt,
			fee = GatheringFee(command.maleFee, command.femaleFee),
			earlyBirdDiscountRate = command.earlyBirdDiscountRate,
			earlyBirdCapacity = command.earlyBirdCapacity,
			discountFee = GatheringFee.optional(command.discountMaleFee, command.discountFemaleFee),
			maxParticipants = gathering.maxParticipants,
			now = timeGenerator.now(),
		)
		val saved: GatheringSchedule = saveGatheringSchedulePort.save(schedule)
		// 일정 저장으로 발급된 id를 상품에 채워 함께 저장한다. (같은 트랜잭션 — 원자적)
		saveGatheringProductPort.saveAll(schedule.products.withScheduleId(saved.id))
		return saved
	}
}
```

import 추가: `import com.org.oneulsogae.admin.gathering.command.application.port.out.SaveGatheringProductPort`. 클래스 KDoc에 상품 저장 문장 추가.

- [ ] **Step 4: 유닛 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.GatheringScheduleTest" --tests "com.org.oneulsogae.domain.gathering.GatheringProductsTest"`
Expected: PASS

- [ ] **Step 5: E2E 검증 추가**

`AdminGatheringScheduleE2ETest.kt`:

import 추가:

```kotlin
import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProductEntity
```

헬퍼 추가(`savedById` 아래):

```kotlin
	fun productsByScheduleId(scheduleId: Long): List<GatheringProductEntity> {
		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		return IntegrationUtil.getQuery().selectFrom(product).where(product.scheduleId.eq(scheduleId)).fetch()
	}
```

첫 번째 context("어드민이 유효한 일정을 생성하면")의 `saved.discountMaleFee shouldBe 9000` 다음에 추가:

```kotlin
				// 성별·티어별 상품이 함께 생성된다: 남/녀 × (NORMAL, EARLY_BIRD, DISCOUNT) = 6행.
				val products: List<GatheringProductEntity> = productsByScheduleId(scheduleId)
				products.size shouldBe 6
				products.first { it.gender == Gender.MALE && it.type == GatheringProductType.NORMAL }.price shouldBe 10000
				products.first { it.gender == Gender.FEMALE && it.type == GatheringProductType.NORMAL }.price shouldBe 8000
				// 얼리버드가 = 정가 × 70% (할인율 30).
				products.first { it.gender == Gender.MALE && it.type == GatheringProductType.EARLY_BIRD }.price shouldBe 7000
				products.first { it.gender == Gender.FEMALE && it.type == GatheringProductType.EARLY_BIRD }.price shouldBe 5600
				products.first { it.gender == Gender.MALE && it.type == GatheringProductType.DISCOUNT }.price shouldBe 9000
				products.first { it.gender == Gender.FEMALE && it.type == GatheringProductType.DISCOUNT }.price shouldBe 7000
				products.all { it.gatheringId == gatheringId } shouldBe true
```

두 번째 context("종료 시각·특가 없이 정상가만 생성하면")의 마지막 assertion 다음에 추가:

```kotlin
					// 정가만 있으면 상품은 남/녀 NORMAL 2행이다.
					val products: List<GatheringProductEntity> = productsByScheduleId(scheduleId)
					products.size shouldBe 2
					products.all { it.type == GatheringProductType.NORMAL } shouldBe true
```

`afterTest`에 products 정리 추가(첫 줄에):

```kotlin
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
```

- [ ] **Step 6: E2E 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminGatheringScheduleE2ETest"`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringSchedule.kt \
  oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/CreateGatheringScheduleService.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringScheduleTest.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminGatheringScheduleE2ETest.kt
git commit -m "feat(gathering): 일정 생성 시 성별·티어별 상품 함께 생성"
```

---

### Task 4: 참가(명령) 경로 전환 — JoiningSchedule을 저장 가격 기반으로

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoiningSchedule.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringScheduleAdapter.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/JoiningScheduleTest.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/admin/gathering/AdminGatheringMemberE2ETest.kt`

**Interfaces:**
- Consumes: Task 2 `GatheringProductJpaRepository.findByScheduleId`, `GatheringProductEntity`, `GatheringProductType`
- Produces: `JoiningSchedule(id, gatheringId, status, maleFee, femaleFee, maleRemaining, femaleRemaining, earlyBirdRemaining, earlyBirdMaleFee: Int?, earlyBirdFemaleFee: Int?, discountMaleFee, discountFemaleFee)` — `earlyBirdDiscountRate` 파라미터가 사라지고 저장된 얼리버드가 2개로 대체. `register(gender)` 동작·시그니처 불변.

- [ ] **Step 1: 유닛 테스트 갱신 (실패 확인)**

`JoiningScheduleTest.kt`의 `schedule()` 헬퍼와 얼리버드 케이스를 저장 가격 기반으로 변경:

```kotlin
	fun schedule(
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
		maleRemaining: Int = 4,
		femaleRemaining: Int = 4,
		earlyBirdRemaining: Int? = null,
		earlyBirdMaleFee: Int? = null,
		earlyBirdFemaleFee: Int? = null,
		discountMaleFee: Int? = null,
		discountFemaleFee: Int? = null,
	): JoiningSchedule =
		JoiningSchedule(
			id = 1L,
			gatheringId = 10L,
			status = status,
			maleFee = 10000,
			femaleFee = 8000,
			maleRemaining = maleRemaining,
			femaleRemaining = femaleRemaining,
			earlyBirdRemaining = earlyBirdRemaining,
			earlyBirdMaleFee = earlyBirdMaleFee,
			earlyBirdFemaleFee = earlyBirdFemaleFee,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
		)
```

"얼리버드가 유효한 일정에 신청하면" 케이스의 대상 생성·검증 변경:

```kotlin
				val target: JoiningSchedule = schedule(earlyBirdRemaining = 2, earlyBirdFemaleFee = 5600)

				val pricing: JoinPricing = target.register(Gender.FEMALE)

				pricing.amount shouldBe 5600 // 저장된 얼리버드가
```

"얼리버드가 소진되고 할인가가 있는 일정에 신청하면" 케이스의 대상 생성 변경:

```kotlin
				val target: JoiningSchedule = schedule(
					earlyBirdRemaining = 0,
					earlyBirdMaleFee = 7000,
					discountMaleFee = 9000,
				)
```

나머지 케이스는 그대로 컴파일된다(할인율 파라미터 미사용).

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.JoiningScheduleTest"`
Expected: 컴파일 실패 (`earlyBirdMaleFee` 파라미터 없음)

- [ ] **Step 2: JoiningSchedule 구현 변경**

`JoiningSchedule.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.core.gathering.command.domain

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode

/**
 * 참가 접수 대상 일정(command 도메인 모델). 접수 규칙(판매 상태·성별 여분 검증)과
 * 확정가 계산(얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 정가), 여분 차감을 캡슐화한다.
 * 가격은 gathering_products에 저장된 성별·티어별 확정 금액을 어댑터가 실어준다(율 계산 없음).
 * 금액 티어 규칙은 query read model(GatheringScheduleView)에도 있지만 CQRS 원칙에 따라 공유하지 않고 각자 구현한다.
 */
class JoiningSchedule(
	val id: Long,
	val gatheringId: Long,
	val status: GatheringScheduleStatus,
	val maleFee: Int,
	val femaleFee: Int,
	var maleRemaining: Int,
	var femaleRemaining: Int,
	var earlyBirdRemaining: Int?,
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
) {

	/** [gender] 성별로 접수한다. 검증 통과 시 확정가를 계산하고 해당 성별 여분(얼리버드 적용 시 얼리버드 여분 포함)을 차감한다. */
	fun register(gender: Gender): JoinPricing {
		validateRegistrable(gender)
		val earlyBirdFee: Int? = earlyBirdFeeFor(gender)
		val amount: Int = earlyBirdFee
			?: (if (earlyBirdSoldOut()) discountFeeFor(gender) else null)
			?: feeFor(gender)
		decrementRemaining(gender)
		val earlyBirdApplied: Boolean = earlyBirdFee != null
		if (earlyBirdApplied) {
			earlyBirdRemaining = checkNotNull(earlyBirdRemaining) - 1
		}
		return JoinPricing(amount = amount, earlyBirdApplied = earlyBirdApplied)
	}

	private fun validateRegistrable(gender: Gender) {
		if (status != GatheringScheduleStatus.SCHEDULED) {
			throw BusinessException(GatheringErrorCode.GATHERING_SCHEDULE_NOT_OPEN)
		}
		if (remainingFor(gender) <= 0) {
			throw BusinessException(GatheringErrorCode.GATHERING_SOLD_OUT)
		}
	}

	private fun feeFor(gender: Gender): Int =
		if (gender == Gender.MALE) maleFee else femaleFee

	private fun discountFeeFor(gender: Gender): Int? =
		if (gender == Gender.MALE) discountMaleFee else discountFemaleFee

	private fun remainingFor(gender: Gender): Int =
		if (gender == Gender.MALE) maleRemaining else femaleRemaining

	private fun decrementRemaining(gender: Gender) {
		if (gender == Gender.MALE) maleRemaining -= 1 else femaleRemaining -= 1
	}

	private fun earlyBirdSoldOut(): Boolean {
		val remaining: Int? = earlyBirdRemaining
		return remaining != null && remaining <= 0
	}

	/** 얼리버드 티어가 존재하고 미소진일 때만 해당 성별의 저장된 얼리버드가. 그 외 null. */
	private fun earlyBirdFeeFor(gender: Gender): Int? {
		if (earlyBirdRemaining == null || earlyBirdSoldOut()) return null
		return if (gender == Gender.MALE) earlyBirdMaleFee else earlyBirdFemaleFee
	}
}
```

- [ ] **Step 3: 어댑터 getForUpdate 변경**

`GatheringScheduleAdapter.kt`:

생성자에 리포지토리 추가:

```kotlin
class GatheringScheduleAdapter(
	private val gatheringScheduleJpaRepository: GatheringScheduleJpaRepository,
	private val gatheringProductJpaRepository: GatheringProductJpaRepository,
) : SaveGatheringSchedulePort,
```

`getForUpdate` 교체:

```kotlin
	// 참가 접수용: 비관적 쓰기 락으로 잠근 일정을 상품 가격과 함께 core 도메인으로 투영한다.
	override fun getForUpdate(scheduleId: Long): JoiningSchedule? =
		gatheringScheduleJpaRepository.findByIdForUpdate(scheduleId)?.let { entity: GatheringScheduleEntity ->
			val products: List<GatheringProductEntity> = gatheringProductJpaRepository.findByScheduleId(scheduleId)
			fun priceOf(gender: Gender, type: GatheringProductType): Int? =
				products.firstOrNull { product: GatheringProductEntity -> product.gender == gender && product.type == type }?.price
			JoiningSchedule(
				id = checkNotNull(entity.id),
				gatheringId = entity.gatheringId,
				status = entity.status,
				maleFee = checkNotNull(priceOf(Gender.MALE, GatheringProductType.NORMAL)) { "정가 상품이 없습니다: $scheduleId" },
				femaleFee = checkNotNull(priceOf(Gender.FEMALE, GatheringProductType.NORMAL)) { "정가 상품이 없습니다: $scheduleId" },
				maleRemaining = entity.maleRemaining,
				femaleRemaining = entity.femaleRemaining,
				earlyBirdRemaining = entity.earlyBirdRemaining,
				earlyBirdMaleFee = priceOf(Gender.MALE, GatheringProductType.EARLY_BIRD),
				earlyBirdFemaleFee = priceOf(Gender.FEMALE, GatheringProductType.EARLY_BIRD),
				discountMaleFee = priceOf(Gender.MALE, GatheringProductType.DISCOUNT),
				discountFemaleFee = priceOf(Gender.FEMALE, GatheringProductType.DISCOUNT),
			)
		}
```

import 추가:

```kotlin
import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.repository.GatheringProductJpaRepository
```

클래스 KDoc에 한 줄 추가: `잠금 조회는 gathering_products의 저장 가격을 함께 실어 투영한다.`

- [ ] **Step 4: 유닛 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.JoiningScheduleTest"`
Expected: PASS

- [ ] **Step 5: E2E 픽스처 갱신**

`PaymentsCompleteE2ETest.kt` — 일정 생성 헬퍼(54행 부근 `GatheringScheduleEntityFixture.create(...)`를 감싼 함수)가 만든 scheduleId에 대해 products를 함께 persist하도록 수정한다. 헬퍼가 `earlyBirdDiscountRate`/`earlyBirdCapacity`/(있다면 `discountMaleFee` 등) 파라미터를 받으므로, 일정 persist 직후에 추가:

```kotlin
			GatheringProductEntityFixture.tierSet(
				gatheringId = gatheringId,
				scheduleId = scheduleId,
				maleFee = 10000,
				femaleFee = 8000,
				earlyBirdDiscountRate = earlyBirdDiscountRate,
				discountMaleFee = discountMaleFee,
				discountFemaleFee = discountFemaleFee,
			).forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
```

(헬퍼에 할인가 파라미터가 없으면 `discountMaleFee = null, discountFemaleFee = null`로 넘긴다. 헬퍼가 scheduleId를 반환하기 전에 위 코드를 넣고, 필요한 import — `GatheringProductEntityFixture`, `GatheringProductEntity`, `QGatheringProductEntity` — 를 추가한다.)

`afterTest`(또는 정리 블록)에 추가:

```kotlin
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
```

`AdminGatheringMemberE2ETest.kt` — 31행 부근 일정 픽스처(`earlyBirdDiscountRate = if (earlyBirdApplied) 30 else null` 사용)도 동일하게, 일정 persist 직후 products를 persist한다:

```kotlin
			GatheringProductEntityFixture.tierSet(
				gatheringId = gatheringId,
				scheduleId = scheduleId,
				earlyBirdDiscountRate = if (earlyBirdApplied) 30 else null,
			).forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
```

같은 방식으로 import·afterTest 정리 추가.

- [ ] **Step 6: E2E 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCompleteE2ETest" --tests "com.org.oneulsogae.admin.gathering.AdminGatheringMemberE2ETest"`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoiningSchedule.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringScheduleAdapter.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/JoiningScheduleTest.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/admin/gathering/AdminGatheringMemberE2ETest.kt
git commit -m "refactor(gathering): 참가 접수 확정가를 상품 저장 가격 기반으로 전환"
```

---

### Task 5: core 조회 경로 전환 — GatheringScheduleView products 기반

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/query/dto/GatheringProductView.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/query/dto/GatheringScheduleView.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/query/GetGatheringDaoImpl.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/offline/OfflineGatheringDetailE2ETest.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCheckoutE2ETest.kt`

**Interfaces:**
- Consumes: `GatheringProductType`, `QGatheringProductEntity`(Task 2 엔티티의 Q타입)
- Produces:
  - `GatheringProductView(gender: Gender, type: GatheringProductType, price: Int)`
  - `GatheringScheduleView(id, startAt, endAt, maleRemaining, femaleRemaining, earlyBirdCapacity, earlyBirdRemaining, status, products: List<GatheringProductView>)` — **공개 메서드(`feeFor`/`discountFeeFor`/`soldOutFor`/`earlyBirdFeeFor`/`salePriceFor`/`earlyBirdSoldOut`) 시그니처 불변** → `ProductResponse`·`GatheringDetailResponse`는 수정 불필요.

- [ ] **Step 1: read model 변경**

`GatheringProductView.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender

/** 일정의 성별·티어별 가격 상품 한 건(read model). 금액은 저장된 확정가다. */
data class GatheringProductView(
	val gender: Gender,
	val type: GatheringProductType,
	val price: Int,
)
```

`GatheringScheduleView.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.core.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.user.Gender
import java.time.LocalDateTime

/**
 * 유저용 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 * 가격은 gathering_products에 저장된 성별·티어별 확정 금액([products])으로 가진다(율 계산 없음).
 * 얼리버드 선착순 수량([earlyBirdCapacity]·[earlyBirdRemaining])은 남/녀 공유 카운터라 일정이 가진다.
 * 남/녀 여분([maleRemaining]·[femaleRemaining])은 해당 성별 소진 판정([soldOutFor])에 쓴다.
 * 금액 티어 계산은 offline 상세 응답과 결제 체크아웃 응답이 공유하므로 이 read model에 캡슐화한다.
 */
data class GatheringScheduleView(
	val id: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val maleRemaining: Int,
	val femaleRemaining: Int,
	val earlyBirdCapacity: Int?,
	val earlyBirdRemaining: Int?,
	val status: GatheringScheduleStatus,
	val products: List<GatheringProductView>,
) {

	/** 얼리버드 소진 여부. 티어가 있고([earlyBirdRemaining] non-null) 남은 개수가 0 이하일 때만 true. */
	val earlyBirdSoldOut: Boolean
		get() = earlyBirdRemaining != null && earlyBirdRemaining <= 0

	/** [gender] 성별의 정가. */
	fun feeFor(gender: Gender): Int =
		checkNotNull(priceFor(gender, GatheringProductType.NORMAL)) { "정가 상품이 없습니다: $id" }

	/** [gender] 성별의 할인가(얼리버드 소진 시 적용 대상). 없으면 null. */
	fun discountFeeFor(gender: Gender): Int? =
		priceFor(gender, GatheringProductType.DISCOUNT)

	/** [gender] 성별 정원 소진 여부. */
	fun soldOutFor(gender: Gender): Boolean =
		(if (gender == Gender.MALE) maleRemaining else femaleRemaining) <= 0

	/** [gender] 성별의 얼리버드가(저장 금액). 얼리버드 티어가 존재하고 미소진일 때만 반환하고, 없거나 소진이면 null. */
	fun earlyBirdFeeFor(gender: Gender): Int? {
		if (earlyBirdRemaining == null || earlyBirdSoldOut) return null
		return priceFor(gender, GatheringProductType.EARLY_BIRD)
	}

	/** [gender] 성별의 서버 확정 실결제가: 얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 → 정가. */
	fun salePriceFor(gender: Gender): Int =
		earlyBirdFeeFor(gender)
			?: (if (earlyBirdSoldOut) discountFeeFor(gender) else null)
			?: feeFor(gender)

	private fun priceFor(gender: Gender, type: GatheringProductType): Int? =
		products.firstOrNull { product: GatheringProductView -> product.gender == gender && product.type == type }?.price
}
```

- [ ] **Step 2: DAO 변경**

`GetGatheringDaoImpl.kt`의 `findSchedulesByGatheringId` 교체(2쿼리: 일정 목록 → 상품 IN 조회·그룹핑, N+1 없음):

```kotlin
	override fun findSchedulesByGatheringId(gatheringId: Long): List<GatheringScheduleView> {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		val rows: List<GatheringScheduleEntity> = queryFactory
			.selectFrom(schedule)
			.where(schedule.gatheringId.eq(gatheringId))
			.orderBy(schedule.startAt.asc())
			.fetch()
		if (rows.isEmpty()) return emptyList()

		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		val productsBySchedule: Map<Long, List<GatheringProductView>> = queryFactory
			.selectFrom(product)
			.where(product.scheduleId.`in`(rows.map { row: GatheringScheduleEntity -> checkNotNull(row.id) }))
			.fetch()
			.groupBy(
				{ row: GatheringProductEntity -> row.scheduleId },
				{ row: GatheringProductEntity -> GatheringProductView(gender = row.gender, type = row.type, price = row.price) },
			)

		return rows.map { row: GatheringScheduleEntity ->
			GatheringScheduleView(
				id = checkNotNull(row.id),
				startAt = row.startAt,
				endAt = row.endAt,
				maleRemaining = row.maleRemaining,
				femaleRemaining = row.femaleRemaining,
				earlyBirdCapacity = row.earlyBirdCapacity,
				earlyBirdRemaining = row.earlyBirdRemaining,
				status = row.status,
				products = productsBySchedule[row.id] ?: emptyList(),
			)
		}
	}
```

import 추가:

```kotlin
import com.org.oneulsogae.core.gathering.query.dto.GatheringProductView
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProductEntity
```

(`Projections` import는 다른 메서드가 계속 쓴다 — 남긴다.)

- [ ] **Step 3: 컴파일로 소비처 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL — `ProductResponse`/`GatheringDetailResponse`는 공개 메서드만 사용하므로 수정 없이 컴파일된다. 만약 `GatheringScheduleView(...)`를 직접 생성하는 다른 코드가 컴파일 에러로 드러나면 같은 방식(products 주입)으로 수정한다.

- [ ] **Step 4: E2E 픽스처 갱신**

`OfflineGatheringDetailE2ETest.kt` — 가격을 검증하는 각 컨텍스트에서 일정 persist 직후 products persist를 추가한다. 일정 픽스처의 fee 인자와 동일한 값으로 `tierSet`을 만든다. 예(53행 부근, maleFee=10000·femaleFee=8000·rate=30·capacity=5인 일정):

```kotlin
			val scheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
					maleFee = 10000,
					femaleFee = 8000,
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
					/* 기존 인자 유지 */
				),
			).id!!
			GatheringProductEntityFixture.tierSet(
				gatheringId = id,
				scheduleId = scheduleId,
				maleFee = 10000,
				femaleFee = 8000,
				earlyBirdDiscountRate = 30,
			).forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
```

파일 내 모든 `GatheringScheduleEntityFixture.create(...)` 호출(46·53·117·155·183·208행 부근)에 대해, 각 호출의 fee/rate/할인가 인자와 동일한 값으로 `tierSet` persist를 추가한다. 할인가 인자(`discountMaleFee`/`discountFemaleFee`)가 있는 호출은 `tierSet`에도 같은 값을 넘긴다. 가격 미검증 일정(상태만 보는 46행 COMPLETED 일정)도 offline 상세 응답이 `feeFor`를 호출하므로 **반드시 NORMAL 세트를 persist한다**.

import·afterTest 정리 추가(Task 3·4와 동일 패턴):

```kotlin
import com.org.oneulsogae.infra.fixture.GatheringProductEntityFixture
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProductEntity
```

```kotlin
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
```

`PaymentsCheckoutE2ETest.kt` — 56행 부근 일정 생성 헬퍼에도 동일하게 products persist·import·afterTest 정리를 추가한다(헬퍼의 fee·rate·할인가 파라미터를 `tierSet`으로 전달).

- [ ] **Step 5: E2E 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.offline.OfflineGatheringDetailE2ETest" --tests "com.org.oneulsogae.api.payments.PaymentsCheckoutE2ETest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/query/dto/GatheringProductView.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/query/dto/GatheringScheduleView.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/query/GetGatheringDaoImpl.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/offline/OfflineGatheringDetailE2ETest.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCheckoutE2ETest.kt
git commit -m "refactor(gathering): 유저 일정 조회를 상품 저장 가격 기반으로 전환"
```

---

### Task 6: 어드민 조회 경로 전환 — 할인율 대신 얼리버드 금액 노출

**Files:**
- Modify: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/query/dto/AdminGatheringScheduleView.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/query/GetAdminGatheringDaoImpl.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminGatheringDetailResponse.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminGatheringQueryE2ETest.kt`

**Interfaces:**
- Consumes: `QGatheringProductEntity`, `GatheringProductType`
- Produces: `AdminGatheringScheduleView(id, startAt, endAt, maleFee: Int, femaleFee: Int, earlyBirdMaleFee: Int?, earlyBirdFemaleFee: Int?, earlyBirdCapacity: Int?, earlyBirdRemaining: Int?, discountMaleFee: Int?, discountFemaleFee: Int?, status)` — `earlyBirdDiscountRate` 필드 소멸, 응답 JSON도 동일하게 변경(**어드민 프론트 수정 필요**).

- [ ] **Step 1: 뷰·응답 변경**

`AdminGatheringScheduleView.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.admin.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import java.time.LocalDateTime

/**
 * 어드민 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 * 가격은 gathering_products의 저장 금액으로 노출한다: 정가([maleFee]·[femaleFee], 필수),
 * 얼리버드가([earlyBirdMaleFee]·[earlyBirdFemaleFee], 선택 — 할인율이 아니라 확정 금액),
 * 할인가([discountMaleFee]·[discountFemaleFee], 선택). 없는 티어는 null.
 * 얼리버드 선착순 수량([earlyBirdCapacity]·[earlyBirdRemaining])은 일정 테이블 컬럼이다.
 */
data class AdminGatheringScheduleView(
	val id: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val maleFee: Int,
	val femaleFee: Int,
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val earlyBirdCapacity: Int?,
	val earlyBirdRemaining: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
	val status: GatheringScheduleStatus,
)
```

`AdminGatheringDetailResponse.kt`의 `Schedule` — `earlyBirdDiscountRate: Int?` 필드를 다음으로 교체:

```kotlin
			// 얼리버드가(남/녀, 저장 금액). 얼리버드가 없는 일정은 null. (할인율이 아니라 금액으로 노출한다)
			val earlyBirdMaleFee: Int?,
			val earlyBirdFemaleFee: Int?,
```

`Schedule.of`의 매핑도 `earlyBirdDiscountRate = view.earlyBirdDiscountRate` → 다음으로 교체:

```kotlin
					earlyBirdMaleFee = view.earlyBirdMaleFee,
					earlyBirdFemaleFee = view.earlyBirdFemaleFee,
```

- [ ] **Step 2: DAO 변경**

`GetAdminGatheringDaoImpl.kt`의 `findSchedulesByGatheringId` 교체(Task 5와 같은 2쿼리 패턴, 티어를 flat 필드로 투영):

```kotlin
	override fun findSchedulesByGatheringId(gatheringId: Long): List<AdminGatheringScheduleView> {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		val rows: List<GatheringScheduleEntity> = queryFactory
			.selectFrom(schedule)
			.where(schedule.gatheringId.eq(gatheringId))
			.orderBy(schedule.startAt.asc())
			.fetch()
		if (rows.isEmpty()) return emptyList()

		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		val productsBySchedule: Map<Long, List<GatheringProductEntity>> = queryFactory
			.selectFrom(product)
			.where(product.scheduleId.`in`(rows.map { row: GatheringScheduleEntity -> checkNotNull(row.id) }))
			.fetch()
			.groupBy { row: GatheringProductEntity -> row.scheduleId }

		return rows.map { row: GatheringScheduleEntity ->
			val products: List<GatheringProductEntity> = productsBySchedule[row.id] ?: emptyList()
			fun priceOf(gender: Gender, type: GatheringProductType): Int? =
				products.firstOrNull { candidate: GatheringProductEntity -> candidate.gender == gender && candidate.type == type }?.price
			AdminGatheringScheduleView(
				id = checkNotNull(row.id),
				startAt = row.startAt,
				endAt = row.endAt,
				maleFee = checkNotNull(priceOf(Gender.MALE, GatheringProductType.NORMAL)) { "정가 상품이 없습니다: ${row.id}" },
				femaleFee = checkNotNull(priceOf(Gender.FEMALE, GatheringProductType.NORMAL)) { "정가 상품이 없습니다: ${row.id}" },
				earlyBirdMaleFee = priceOf(Gender.MALE, GatheringProductType.EARLY_BIRD),
				earlyBirdFemaleFee = priceOf(Gender.FEMALE, GatheringProductType.EARLY_BIRD),
				earlyBirdCapacity = row.earlyBirdCapacity,
				earlyBirdRemaining = row.earlyBirdRemaining,
				discountMaleFee = priceOf(Gender.MALE, GatheringProductType.DISCOUNT),
				discountFemaleFee = priceOf(Gender.FEMALE, GatheringProductType.DISCOUNT),
				status = row.status,
			)
		}
	}
```

import 추가:

```kotlin
import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProductEntity
```

- [ ] **Step 3: E2E 갱신**

`AdminGatheringQueryE2ETest.kt` — 139·146행 부근 일정 픽스처들 직후에 Task 5와 같은 패턴으로 `tierSet` persist를 추가한다(각 픽스처의 fee/rate 인자와 동일 값). 상세 응답을 검증하는 부분에서 `earlyBirdDiscountRate` 관련 assertion이 있으면 저장 금액 기준으로 교체한다. 예: `"data.schedules[0].earlyBirdDiscountRate", 30` → 

```kotlin
					body("data.schedules[0].earlyBirdMaleFee", 7000)
					body("data.schedules[0].earlyBirdFemaleFee", 5600)
```

(실제 assertion DSL 형태는 파일의 기존 스타일을 따른다.) import·afterTest 정리도 동일하게 추가.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminGatheringQueryE2ETest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/query/dto/AdminGatheringScheduleView.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/query/GetAdminGatheringDaoImpl.kt \
  oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminGatheringDetailResponse.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminGatheringQueryE2ETest.kt
git commit -m "refactor(gathering): 어드민 일정 조회를 상품 저장 가격 기반으로 전환"
```

---

### Task 7: 수축 단계 — 스케줄의 가격 컬럼·필드 제거

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/entity/GatheringScheduleEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/mapper/GatheringScheduleMapper.kt`
- Modify: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/GatheringSchedule.kt`
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/GatheringScheduleEntityFixture.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringScheduleTest.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminGatheringScheduleE2ETest.kt`
- Test(Modify): 컴파일 에러로 드러나는 나머지 픽스처 호출부(fee 인자 제거)

**Interfaces:**
- Consumes: Task 3~6에서 전환 완료된 경로들(이 Task 이후 가격의 진실은 gathering_products뿐)
- Produces: `GatheringScheduleEntity`(가격 컬럼 없음), `GatheringSchedule(id, gatheringId, startAt, endAt, maleCapacity, femaleCapacity, earlyBirdCapacity, status, products)` — `fee`/`earlyBirdDiscountRate`/`discountFee` 프로퍼티 소멸(**`create()` 입력 파라미터는 불변**), `GatheringScheduleEntityFixture.create(...)`에서 fee 관련 파라미터 소멸

- [ ] **Step 1: 엔티티에서 가격 컬럼 제거**

`GatheringScheduleEntity.kt`에서 다음 프로퍼티 5개를 삭제: `maleFee`, `femaleFee`, `earlyBirdDiscountRate`, `discountMaleFee`, `discountFemaleFee`. (`earlyBirdCapacity`·`earlyBirdRemaining`·정원·여분·시간·상태는 유지)
KDoc의 참가비 문단을 다음으로 교체:

```
 * 가격은 이 엔티티가 갖지 않고 gathering_products([GatheringProductEntity])가 성별·티어별 행으로 가진다.
 * 얼리버드 선착순 수량([earlyBirdCapacity]·[earlyBirdRemaining])은 남/녀 공유 카운터라 일정이 가진다.
```

- [ ] **Step 2: 도메인에서 가격 필드 제거**

`GatheringSchedule.kt` — data class 프로퍼티에서 `fee`, `earlyBirdDiscountRate`, `discountFee` 3개를 삭제한다. `create()`의 **파라미터는 그대로 유지**하고(products 구성에 필요), 반환하는 `GatheringSchedule(...)` 생성에서 해당 3개 인자만 제거한다:

```kotlin
			return GatheringSchedule(
				gatheringId = gatheringId,
				startAt = startAt,
				endAt = endAt,
				maleCapacity = genderCapacity,
				femaleCapacity = genderCapacity,
				earlyBirdCapacity = earlyBirdCapacity,
				products = GatheringProducts.create(
					gatheringId = gatheringId,
					fee = fee,
					earlyBirdDiscountRate = earlyBirdDiscountRate,
					discountFee = discountFee,
				),
			)
```

KDoc의 참가비 문단을 "가격은 [products]가 성별·티어별 확정 금액으로 가진다. 얼리버드 선착순 수량은 [earlyBirdCapacity]로 가진다."로 교체.

- [ ] **Step 3: 매퍼·픽스처 갱신**

`GatheringScheduleMapper.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.infra.gathering.command.mapper

import com.org.oneulsogae.admin.gathering.command.domain.GatheringSchedule
import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity

fun GatheringScheduleEntity.toDomain(): GatheringSchedule =
	GatheringSchedule(
		id = id ?: 0,
		gatheringId = gatheringId,
		startAt = startAt,
		endAt = endAt,
		maleCapacity = maleCapacity,
		femaleCapacity = femaleCapacity,
		earlyBirdCapacity = earlyBirdCapacity,
		status = status,
	)

fun GatheringSchedule.toEntity(): GatheringScheduleEntity =
	GatheringScheduleEntity(
		gatheringId = gatheringId,
		startAt = startAt,
		endAt = endAt,
		maleCapacity = maleCapacity,
		femaleCapacity = femaleCapacity,
		// 저장 시 남/녀 여분은 각 성별 정원으로 초기화한다.
		maleRemaining = maleCapacity,
		femaleRemaining = femaleCapacity,
		earlyBirdCapacity = earlyBirdCapacity,
		// 저장 시 남은 개수는 정원(earlyBirdCapacity)으로 초기화한다.
		earlyBirdRemaining = earlyBirdCapacity,
		status = status,
	).also { if (id != 0L) it.id = id }
```

(toDomain은 products를 싣지 않는다 — 상태 전이 등 로드 경로는 products가 불필요하고, 기본값 빈 컬렉션이 쓰인다.)

`GatheringScheduleEntityFixture.kt` — `maleFee`, `femaleFee`, `earlyBirdDiscountRate`, `discountMaleFee`, `discountFemaleFee` 파라미터와 전달 인자를 삭제한다. (`earlyBirdCapacity`·`earlyBirdRemaining` 유지)

- [ ] **Step 4: 컴파일 에러 정리**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: 픽스처에 fee 인자를 넘기던 테스트 호출부들이 컴파일 에러로 드러난다. 각 파일에서 **fee 관련 인자만 삭제**한다(Task 4~6에서 products persist는 이미 추가돼 있으므로 다른 변경은 불필요):
- `PaymentsCompleteE2ETest.kt`: `maleFee = 10000, femaleFee = 8000,`와 `earlyBirdDiscountRate = earlyBirdDiscountRate,` 류 인자 삭제 (헬퍼 파라미터 자체는 tierSet에 넘기므로 유지)
- `PaymentsCheckoutE2ETest.kt`: 동일
- `OfflineGatheringDetailE2ETest.kt`: `maleFee`/`femaleFee`/`earlyBirdDiscountRate`/`discountMaleFee`/`discountFemaleFee` 인자 삭제
- `AdminGatheringQueryE2ETest.kt`: 동일
- `AdminGatheringMemberE2ETest.kt`: `earlyBirdDiscountRate = if (earlyBirdApplied) 30 else null,` 삭제 (`earlyBirdCapacity`/`earlyBirdRemaining` 유지)

`AdminGatheringScheduleE2ETest.kt` — 저장 엔티티의 가격 컬럼 assertion을 삭제한다:
- `saved.maleFee shouldBe 10000`, `saved.femaleFee shouldBe 8000`, `saved.earlyBirdDiscountRate shouldBe 30`, `saved.discountMaleFee shouldBe 9000` 삭제 (products assertion이 Task 3에서 이미 대체)
- `saved.earlyBirdDiscountRate shouldBe null` 삭제

`GatheringScheduleTest.kt`:
- 첫 번째 it의 `schedule.fee shouldBe fee`와 `schedule.earlyBirdDiscountRate shouldBe earlyBirdRate` 삭제
- `scheduleWith` 헬퍼를 다음으로 교체:

```kotlin
		fun scheduleWith(status: GatheringScheduleStatus): GatheringSchedule =
			GatheringSchedule(
				id = 1L, gatheringId = 1L, startAt = start, endAt = end,
				maleCapacity = cap, femaleCapacity = cap,
				earlyBirdCapacity = null, status = status,
			)
```

- [ ] **Step 5: 전체 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test`
Expected: PASS (전 도메인 유닛 + E2E)

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor(gathering): 일정 테이블에서 가격 컬럼 제거 (진실은 gathering_products)"
```

---

## 완료 후 사용자 안내 사항 (코드 외)

- **운영 DDL 미적용**: 스펙 문서 8절의 SQL(CREATE + 백필 + DROP)을 배포 전 순서대로 적용해야 한다.
- **어드민 프론트 수정 필요**: 모임 상세 `schedules[].earlyBirdDiscountRate`(율%)가 `earlyBirdMaleFee`/`earlyBirdFemaleFee`(금액)로 대체됨. 유저 쪽(offline 상세·체크아웃) 응답과 어드민 일정 생성 입력은 불변.
