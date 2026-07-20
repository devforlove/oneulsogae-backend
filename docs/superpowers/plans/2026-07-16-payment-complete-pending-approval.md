# 결제완료 → 대기 등록 → 운영자 승인/거절 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 유저가 결제를 완료하면 `gathering_members`에 승인대기(PENDING)로 등록하고, 운영자가 승인/거절하는 흐름을 구현한다.

**Architecture:** 헥사고날. core `gathering` 도메인에 command(참가 등록: 검증·여분 차감·PENDING insert)를 신설하고, core `payments` command가 그 in-port를 호출해 결제 기록을 남긴다. 운영자 승인/거절/목록은 자립 모듈 `oneulsogae-admin`의 자체 도메인·포트로 구현하고 infra 어댑터가 잇는다.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 4.0.6, Spring Data JPA + QueryDSL, Kotest(유닛), Testcontainers E2E.

**Spec:** `docs/superpowers/specs/2026-07-16-payment-complete-pending-approval-design.md`

## Global Constraints

- 응답·주석·커밋 메시지는 한국어.
- 타입 명시: 변수·반환 타입·람다 파라미터 타입 생략 금지.
- 도메인 검증은 도메인 모델의 `validate…()`로 캡슐화(서비스 if-throw 나열 금지).
- Controller는 in-port UseCase만 주입. 다른 도메인 접근은 그 도메인의 in-port로.
- command 조회 포트와 query dao는 공유하지 않는다(CQRS).
- E2E는 `AbstractIntegrationSupport` + `IntegrationUtil` + 픽스처, 리포지토리 직접 의존 금지.
- 커밋 형식: `<type>(<domain>): <설명>` (예: `feat(gathering): …`, `feat(payments): …`).
- prod `ddl-auto: validate` — 스키마 변경은 `docs/migration/*.sql` 동반.
- 테스트 실행: `./gradlew :oneulsogae-api:test --tests "<FQCN>"` (E2E는 Docker 필요).

---

### Task 1: 상태 모델 + gathering_members 스키마 확장

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/gathering/GatheringMemberStatus.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/entity/GatheringMemberEntity.kt`
- Create: `docs/migration/gathering_members_schedule_id_gender.sql`
- Create: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/GatheringMemberEntityFixture.kt`

**Interfaces:**
- Produces: `GatheringMemberStatus.PENDING/REJECTED`, `GatheringMemberEntity(gatheringId, scheduleId, userId, gender, status, earlyBirdApplied)`, `GatheringMemberEntityFixture.create(...)`

- [ ] **Step 1: GatheringMemberStatus에 PENDING·REJECTED 추가**

`GatheringMemberStatus.kt` 전체를 다음으로 교체:

```kotlin
package com.org.oneulsogae.common.gathering

/** 모임 참가자([com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity])의 상태. */
enum class GatheringMemberStatus(val description: String) {

	/** 승인대기. 결제완료 접수 직후 운영자 승인을 기다리는 상태. 정원에 포함된다. */
	PENDING("승인대기"),

	/** 참가. 모임에 정상 참가 중인 상태. */
	JOINED("참가"),

	/** 거절. 운영자가 접수를 거절한 상태(입금 미확인 등). 정원에서 제외된다. */
	REJECTED("거절"),

	/** 참가취소. 모임 참가를 취소한 상태. */
	CANCELED("참가취소"),
}
```

- [ ] **Step 2: GatheringMemberEntity에 schedule_id·gender·early_bird_applied 추가 + 유니크 제약 변경**

`GatheringMemberEntity.kt` 전체를 다음으로 교체:

```kotlin
package com.org.oneulsogae.infra.gathering.command.entity

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 한 모임 일정([GatheringScheduleEntity])에 참가 신청한 참가자 한 명을 (schedule_id, user_id) 한 쌍의 행으로 정규화한 엔티티.
 * 참가는 일정(회차) 단위다. 소속 모임은 [gatheringId]로 함께 보관한다(일정 조인 없이 모임 단위 조회 커버).
 * 주최자는 [GatheringEntity.userId]로만 표현하므로 이 엔티티는 순수 참가자만 보관한다.
 * (schedule_id, user_id) 유니크 제약으로 같은 사용자의 같은 일정 중복 신청을 막고, (user_id) 인덱스로 사용자별 참가 조회를 커버한다.
 * [earlyBirdApplied]는 접수 시 얼리버드 여분을 소진했는지 기록한다(거절 시 early_bird_remaining 복원 판단).
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gathering_members",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_schedule_id_user_id", columnNames = ["schedule_id", "user_id"]),
	],
	indexes = [
		// 사용자별 참가 모임 조회.
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class GatheringMemberEntity(
	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	@Column(name = "schedule_id", nullable = false)
	val scheduleId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 참가자 성별. 거절/취소 시 어느 성별 여분 카운터를 복원할지 판단한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	var gender: Gender,

	/** 참가자 상태. 승인대기·참가·거절·참가취소를 구분한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: GatheringMemberStatus,

	/** 접수 시 얼리버드 여분을 소진했는지 여부. */
	@Column(name = "early_bird_applied", nullable = false)
	var earlyBirdApplied: Boolean = false,
) : BaseEntity()
```

- [ ] **Step 3: 마이그레이션 SQL 작성**

`docs/migration/gathering_members_schedule_id_gender.sql` 생성:

```sql
-- 참가를 일정(회차) 단위로 전환한다: schedule_id·gender·early_bird_applied 컬럼 추가, 유니크 제약을 (schedule_id, user_id)로 변경.
-- gathering_members는 아직 어떤 기능도 쓰지 않는 빈 테이블이므로 NOT NULL 추가가 안전하다.
ALTER TABLE gathering_members ADD COLUMN schedule_id BIGINT NOT NULL AFTER gathering_id;
ALTER TABLE gathering_members ADD COLUMN gender VARCHAR(50) NOT NULL AFTER user_id;
ALTER TABLE gathering_members ADD COLUMN early_bird_applied BOOLEAN NOT NULL DEFAULT FALSE AFTER gender;
ALTER TABLE gathering_members DROP INDEX ux_gathering_id_user_id;
ALTER TABLE gathering_members ADD UNIQUE INDEX ux_schedule_id_user_id (schedule_id, user_id);
```

- [ ] **Step 4: 엔티티 픽스처 작성**

`oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/GatheringMemberEntityFixture.kt` 생성:

```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity

/**
 * [GatheringMemberEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * (created_at은 저장 시 JPA Auditing이 채운다)
 */
object GatheringMemberEntityFixture {

	fun create(
		gatheringId: Long = 1L,
		scheduleId: Long = 1L,
		userId: Long = 1L,
		gender: Gender = Gender.MALE,
		status: GatheringMemberStatus = GatheringMemberStatus.PENDING,
		earlyBirdApplied: Boolean = false,
	): GatheringMemberEntity =
		GatheringMemberEntity(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			userId = userId,
			gender = gender,
			status = status,
			earlyBirdApplied = earlyBirdApplied,
		)
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-common:compileKotlin :oneulsogae-infra:compileKotlin :oneulsogae-infra:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add oneulsogae-common oneulsogae-infra docs/migration/gathering_members_schedule_id_gender.sql
git commit -m "feat(gathering): 참가자 상태 PENDING·REJECTED 추가 및 일정 단위 참가로 스키마 확장"
```

---

### Task 2: core gathering command 도메인 모델 (TDD)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/GatheringErrorCode.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoiningSchedule.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoinPricing.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/GatheringMember.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/JoiningScheduleTest.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringMemberTest.kt`

**Interfaces:**
- Produces:
  - `JoiningSchedule(id, gatheringId, status, maleFee, femaleFee, maleRemaining, femaleRemaining, earlyBirdRemaining, earlyBirdDiscountRate, discountMaleFee, discountFemaleFee)` — `fun register(gender: Gender): JoinPricing`
  - `data class JoinPricing(val amount: Int, val earlyBirdApplied: Boolean)`
  - `GatheringMember(id, gatheringId, scheduleId, userId, gender, status, earlyBirdApplied)` — `fun validateReRegistrable()`, `fun revive(gender: Gender, earlyBirdApplied: Boolean)`, `companion fun pending(gatheringId, scheduleId, userId, gender, earlyBirdApplied)`
  - `GatheringErrorCode.GATHERING_SCHEDULE_NOT_FOUND / GATHERING_SCHEDULE_NOT_OPEN / GATHERING_SOLD_OUT / GATHERING_ALREADY_JOINED`

- [ ] **Step 1: 에러 코드 추가**

`GatheringErrorCode.kt`의 enum 본문에 아래 항목을 `GATHERING_NOT_FOUND` 뒤에 추가:

```kotlin
	/** 참가 신청 대상 일정을 찾지 못함(없거나 요청 모임 소속이 아님). */
	GATHERING_SCHEDULE_NOT_FOUND("GATHERING-002", "모임 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 판매 중(예정 상태)이 아닌 일정에 참가 신청함. */
	GATHERING_SCHEDULE_NOT_OPEN("GATHERING-003", "참가 신청할 수 없는 일정입니다.", HttpStatus.CONFLICT),

	/** 해당 성별 정원이 모두 찼음(승인대기 포함). */
	GATHERING_SOLD_OUT("GATHERING-004", "정원이 마감되었습니다.", HttpStatus.CONFLICT),

	/** 같은 일정에 이미 승인대기 또는 참가 상태의 신청이 있음. */
	GATHERING_ALREADY_JOINED("GATHERING-005", "이미 참가 신청한 일정입니다.", HttpStatus.CONFLICT),
```

- [ ] **Step 2: JoiningSchedule 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/JoiningScheduleTest.kt` 생성:

```kotlin
package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode
import com.org.oneulsogae.core.gathering.command.domain.JoinPricing
import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class JoiningScheduleTest : DescribeSpec({

	fun schedule(
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
		maleRemaining: Int = 4,
		femaleRemaining: Int = 4,
		earlyBirdRemaining: Int? = null,
		earlyBirdDiscountRate: Int? = null,
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
			earlyBirdDiscountRate = earlyBirdDiscountRate,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
		)

	describe("register") {

		context("예정 상태이고 여분이 남은 일정에 신청하면") {
			it("정가로 접수하고 해당 성별 여분만 차감한다") {
				val target: JoiningSchedule = schedule()

				val pricing: JoinPricing = target.register(Gender.MALE)

				pricing.amount shouldBe 10000
				pricing.earlyBirdApplied shouldBe false
				target.maleRemaining shouldBe 3
				target.femaleRemaining shouldBe 4
			}
		}

		context("얼리버드가 유효한 일정에 신청하면") {
			it("얼리버드가로 접수하고 얼리버드 여분도 차감한다") {
				val target: JoiningSchedule = schedule(earlyBirdRemaining = 2, earlyBirdDiscountRate = 30)

				val pricing: JoinPricing = target.register(Gender.FEMALE)

				pricing.amount shouldBe 5600 // 8000 × 70%
				pricing.earlyBirdApplied shouldBe true
				target.earlyBirdRemaining shouldBe 1
				target.femaleRemaining shouldBe 3
			}
		}

		context("얼리버드가 소진되고 할인가가 있는 일정에 신청하면") {
			it("할인가로 접수하고 얼리버드 여분은 차감하지 않는다") {
				val target: JoiningSchedule = schedule(
					earlyBirdRemaining = 0,
					earlyBirdDiscountRate = 30,
					discountMaleFee = 9000,
				)

				val pricing: JoinPricing = target.register(Gender.MALE)

				pricing.amount shouldBe 9000
				pricing.earlyBirdApplied shouldBe false
				target.earlyBirdRemaining shouldBe 0
			}
		}

		context("예정 상태가 아닌 일정에 신청하면") {
			it("GATHERING_SCHEDULE_NOT_OPEN을 던지고 여분을 차감하지 않는다") {
				val target: JoiningSchedule = schedule(status = GatheringScheduleStatus.COMPLETED)

				val exception: BusinessException = shouldThrow<BusinessException> { target.register(Gender.MALE) }

				exception.errorCode shouldBe GatheringErrorCode.GATHERING_SCHEDULE_NOT_OPEN
				target.maleRemaining shouldBe 4
			}
		}

		context("해당 성별 여분이 없는 일정에 신청하면") {
			it("GATHERING_SOLD_OUT을 던진다") {
				val target: JoiningSchedule = schedule(maleRemaining = 0)

				val exception: BusinessException = shouldThrow<BusinessException> { target.register(Gender.MALE) }

				exception.errorCode shouldBe GatheringErrorCode.GATHERING_SOLD_OUT
			}
		}

		context("반대 성별 여분만 없는 일정에 신청하면") {
			it("정상 접수한다") {
				val target: JoiningSchedule = schedule(maleRemaining = 0, femaleRemaining = 1)

				val pricing: JoinPricing = target.register(Gender.FEMALE)

				pricing.amount shouldBe 8000
				target.femaleRemaining shouldBe 0
			}
		}
	}
})
```

> 확인 완료: `GatheringScheduleStatus`는 `SCHEDULED/COMPLETED/CANCELED`, `BusinessException`은 `val errorCode: ErrorCode` 프로퍼티를 가진다.

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `JoiningSchedule` unresolved reference

- [ ] **Step 4: JoinPricing·JoiningSchedule 구현**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoinPricing.kt` 생성:

```kotlin
package com.org.oneulsogae.core.gathering.command.domain

/** 참가 접수 시 확정된 가격 정보. [earlyBirdApplied]가 true면 얼리버드 여분을 소진했다. */
data class JoinPricing(
	val amount: Int,
	val earlyBirdApplied: Boolean,
)
```

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoiningSchedule.kt` 생성:

```kotlin
package com.org.oneulsogae.core.gathering.command.domain

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode

/**
 * 참가 접수 대상 일정(command 도메인 모델). 접수 규칙(판매 상태·성별 여분 검증)과
 * 확정가 계산(얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 정가), 여분 차감을 캡슐화한다.
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
	val earlyBirdDiscountRate: Int?,
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

	/** 얼리버드 티어가 존재하고 미소진일 때만 정상가 × (100 - 할인율) / 100(버림). 그 외 null. */
	private fun earlyBirdFeeFor(gender: Gender): Int? {
		if (earlyBirdRemaining == null || earlyBirdSoldOut()) return null
		return earlyBirdDiscountRate?.let { rate: Int -> feeFor(gender) * (100 - rate) / 100 }
	}
}
```

- [ ] **Step 5: JoiningSchedule 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.JoiningScheduleTest"`
Expected: PASS

- [ ] **Step 6: GatheringMember 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringMemberTest.kt` 생성:

```kotlin
package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode
import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GatheringMemberTest : DescribeSpec({

	fun member(status: GatheringMemberStatus): GatheringMember =
		GatheringMember(
			id = 1L,
			gatheringId = 10L,
			scheduleId = 100L,
			userId = 1000L,
			gender = Gender.MALE,
			status = status,
			earlyBirdApplied = false,
		)

	describe("validateReRegistrable") {

		context("승인대기 상태면") {
			it("GATHERING_ALREADY_JOINED를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					member(GatheringMemberStatus.PENDING).validateReRegistrable()
				}
				exception.errorCode shouldBe GatheringErrorCode.GATHERING_ALREADY_JOINED
			}
		}

		context("참가 상태면") {
			it("GATHERING_ALREADY_JOINED를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					member(GatheringMemberStatus.JOINED).validateReRegistrable()
				}
				exception.errorCode shouldBe GatheringErrorCode.GATHERING_ALREADY_JOINED
			}
		}

		context("거절·참가취소 상태면") {
			it("통과한다") {
				member(GatheringMemberStatus.REJECTED).validateReRegistrable()
				member(GatheringMemberStatus.CANCELED).validateReRegistrable()
			}
		}
	}

	describe("revive") {

		context("거절 상태의 행을 되살리면") {
			it("승인대기로 전환하고 성별·얼리버드 적용 여부를 갱신한다") {
				val target: GatheringMember = member(GatheringMemberStatus.REJECTED)

				target.revive(gender = Gender.FEMALE, earlyBirdApplied = true)

				target.status shouldBe GatheringMemberStatus.PENDING
				target.gender shouldBe Gender.FEMALE
				target.earlyBirdApplied shouldBe true
			}
		}
	}

	describe("pending") {

		it("승인대기 상태의 신규 참가자를 생성한다") {
			val target: GatheringMember = GatheringMember.pending(
				gatheringId = 10L,
				scheduleId = 100L,
				userId = 1000L,
				gender = Gender.MALE,
				earlyBirdApplied = true,
			)

			target.id shouldBe null
			target.status shouldBe GatheringMemberStatus.PENDING
			target.earlyBirdApplied shouldBe true
		}
	}
})
```

- [ ] **Step 7: GatheringMember 구현**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/GatheringMember.kt` 생성:

```kotlin
package com.org.oneulsogae.core.gathering.command.domain

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode

/**
 * 모임 일정 참가자(command 도메인 모델). 결제완료 접수 시 승인대기(PENDING)로 생성되고,
 * 운영자 승인/거절은 admin 모듈이 담당한다. 거절/취소된 행은 재접수 시 [revive]로 되살린다
 * ((schedule_id, user_id) 유니크 제약 때문에 새 행을 만들지 않는다).
 */
class GatheringMember(
	val id: Long? = null,
	val gatheringId: Long,
	val scheduleId: Long,
	val userId: Long,
	var gender: Gender,
	var status: GatheringMemberStatus,
	var earlyBirdApplied: Boolean,
) {

	/** 재접수 가능 여부를 검증한다. 승인대기·참가 상태면 중복 신청이다. */
	fun validateReRegistrable() {
		if (status == GatheringMemberStatus.PENDING || status == GatheringMemberStatus.JOINED) {
			throw BusinessException(GatheringErrorCode.GATHERING_ALREADY_JOINED)
		}
	}

	/** 거절/취소된 행을 승인대기로 되살린다. 이번 접수의 성별·얼리버드 적용 여부로 갱신한다. */
	fun revive(gender: Gender, earlyBirdApplied: Boolean) {
		validateReRegistrable()
		this.status = GatheringMemberStatus.PENDING
		this.gender = gender
		this.earlyBirdApplied = earlyBirdApplied
	}

	companion object {

		/** 승인대기 상태의 신규 참가자를 생성한다. */
		fun pending(gatheringId: Long, scheduleId: Long, userId: Long, gender: Gender, earlyBirdApplied: Boolean): GatheringMember =
			GatheringMember(
				gatheringId = gatheringId,
				scheduleId = scheduleId,
				userId = userId,
				gender = gender,
				status = GatheringMemberStatus.PENDING,
				earlyBirdApplied = earlyBirdApplied,
			)
	}
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.GatheringMemberTest" --tests "com.org.oneulsogae.domain.gathering.JoiningScheduleTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add oneulsogae-core oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering
git commit -m "feat(gathering): 참가 접수 도메인 모델(JoiningSchedule·GatheringMember) 추가"
```

---

### Task 3: 참가 등록 유스케이스 + infra 어댑터

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/in/RegisterGatheringMemberUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/in/command/RegisterGatheringMemberCommand.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/in/result/RegisterGatheringMemberResult.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/out/GetJoiningSchedulePort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/out/SaveJoiningSchedulePort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/out/LoadGatheringMemberPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/out/SaveGatheringMemberPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/RegisterGatheringMemberService.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/repository/GatheringMemberJpaRepository.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/mapper/GatheringMemberMapper.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringMemberAdapter.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/repository/GatheringScheduleJpaRepository.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringScheduleAdapter.kt`

**Interfaces:**
- Consumes: Task 2의 `JoiningSchedule.register`, `GatheringMember.pending/validateReRegistrable/revive`, `JoinPricing`
- Produces:
  - `RegisterGatheringMemberUseCase.register(command: RegisterGatheringMemberCommand): RegisterGatheringMemberResult`
  - `data class RegisterGatheringMemberCommand(val gatheringId: Long, val scheduleId: Long, val userId: Long, val gender: Gender)`
  - `data class RegisterGatheringMemberResult(val memberId: Long, val amount: Int)`
  - `GatheringScheduleJpaRepository.findByIdForUpdate(id: Long): GatheringScheduleEntity?`

- [ ] **Step 1: in-port·command·result 작성**

`port/in/RegisterGatheringMemberUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.`in`

import com.org.oneulsogae.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.oneulsogae.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult

/**
 * 모임 일정 참가 접수 인포트(유스케이스). 결제완료(payments)가 호출한다.
 * 판매 상태·성별 여분·중복 신청을 검증하고 승인대기(PENDING)로 등록하며, 서버 확정가를 돌려준다.
 */
interface RegisterGatheringMemberUseCase {

	fun register(command: RegisterGatheringMemberCommand): RegisterGatheringMemberResult
}
```

`port/in/command/RegisterGatheringMemberCommand.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.`in`.command

import com.org.oneulsogae.common.user.Gender

/** 참가 접수 명령. [gender]는 호출자(payments)가 본인 프로필에서 확정해 넘긴다. */
data class RegisterGatheringMemberCommand(
	val gatheringId: Long,
	val scheduleId: Long,
	val userId: Long,
	val gender: Gender,
)
```

`port/in/result/RegisterGatheringMemberResult.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.`in`.result

/** 참가 접수 결과. [amount]는 접수 시점 여분 기준으로 확정한 실결제가다. */
data class RegisterGatheringMemberResult(
	val memberId: Long,
	val amount: Int,
)
```

- [ ] **Step 2: out-port 4개 작성**

`port/out/GetJoiningSchedulePort.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.out

import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule

/** 참가 접수 대상 일정을 비관적 쓰기 락으로 조회하는 아웃포트. (동시 접수의 여분 차감 직렬화) */
interface GetJoiningSchedulePort {

	fun getForUpdate(scheduleId: Long): JoiningSchedule?
}
```

`port/out/SaveJoiningSchedulePort.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.out

import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule

/** 접수로 차감된 일정 여분(성별·얼리버드)을 반영하는 아웃포트. */
interface SaveJoiningSchedulePort {

	fun save(schedule: JoiningSchedule)
}
```

`port/out/LoadGatheringMemberPort.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.out

import com.org.oneulsogae.core.gathering.command.domain.GatheringMember

/** (schedule, user)의 기존 참가 행을 조회하는 아웃포트. (중복 신청 검증·거절 행 되살림용) */
interface LoadGatheringMemberPort {

	fun loadByScheduleIdAndUserId(scheduleId: Long, userId: Long): GatheringMember?
}
```

`port/out/SaveGatheringMemberPort.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.out

import com.org.oneulsogae.core.gathering.command.domain.GatheringMember

/** 참가 행을 저장(신규 insert 또는 기존 행 갱신)하는 아웃포트. */
interface SaveGatheringMemberPort {

	fun save(member: GatheringMember): GatheringMember
}
```

- [ ] **Step 3: RegisterGatheringMemberService 작성**

`command/application/RegisterGatheringMemberService.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode
import com.org.oneulsogae.core.gathering.command.application.port.`in`.RegisterGatheringMemberUseCase
import com.org.oneulsogae.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.oneulsogae.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult
import com.org.oneulsogae.core.gathering.command.application.port.out.GetJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.application.port.out.LoadGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import com.org.oneulsogae.core.gathering.command.domain.JoinPricing
import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [RegisterGatheringMemberUseCase] 구현.
 * 일정 행을 비관적 락으로 잠근 뒤 접수 규칙(판매 상태·성별 여분·중복 신청)을 검증하고,
 * 확정가 계산·여분 차감 후 승인대기(PENDING) 참가 행을 저장한다.
 * 거절/취소된 기존 행이 있으면 새 행 대신 되살린다((schedule_id, user_id) 유니크 제약).
 */
@Service
@Transactional
class RegisterGatheringMemberService(
	private val getJoiningSchedulePort: GetJoiningSchedulePort,
	private val saveJoiningSchedulePort: SaveJoiningSchedulePort,
	private val loadGatheringMemberPort: LoadGatheringMemberPort,
	private val saveGatheringMemberPort: SaveGatheringMemberPort,
) : RegisterGatheringMemberUseCase {

	override fun register(command: RegisterGatheringMemberCommand): RegisterGatheringMemberResult {
		val schedule: JoiningSchedule = getJoiningSchedulePort.getForUpdate(command.scheduleId)
			?.takeIf { it.gatheringId == command.gatheringId }
			?: throw BusinessException(GatheringErrorCode.GATHERING_SCHEDULE_NOT_FOUND)

		val existing: GatheringMember? = loadGatheringMemberPort.loadByScheduleIdAndUserId(command.scheduleId, command.userId)
		existing?.validateReRegistrable()

		val pricing: JoinPricing = schedule.register(command.gender)
		val member: GatheringMember = existing
			?.also { it.revive(gender = command.gender, earlyBirdApplied = pricing.earlyBirdApplied) }
			?: GatheringMember.pending(
				gatheringId = command.gatheringId,
				scheduleId = command.scheduleId,
				userId = command.userId,
				gender = command.gender,
				earlyBirdApplied = pricing.earlyBirdApplied,
			)

		val saved: GatheringMember = saveGatheringMemberPort.save(member)
		saveJoiningSchedulePort.save(schedule)
		return RegisterGatheringMemberResult(memberId = checkNotNull(saved.id), amount = pricing.amount)
	}
}
```

- [ ] **Step 4: 리포지토리·매퍼·어댑터 작성**

`GatheringMemberJpaRepository.kt` 생성:

```kotlin
package com.org.oneulsogae.infra.gathering.command.repository

import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringMemberJpaRepository : JpaRepository<GatheringMemberEntity, Long> {

	/** (schedule, user)의 참가 행을 조회한다. (schedule_id, user_id) 유니크라 최대 1건. */
	fun findByScheduleIdAndUserId(scheduleId: Long, userId: Long): GatheringMemberEntity?
}
```

`GatheringScheduleJpaRepository.kt` 전체를 다음으로 교체:

```kotlin
package com.org.oneulsogae.infra.gathering.command.repository

import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GatheringScheduleJpaRepository : JpaRepository<GatheringScheduleEntity, Long> {

	/**
	 * 참가 접수의 여분 차감을 위해 비관적 쓰기 락으로 일정 행을 조회한다. (SELECT ... FOR UPDATE)
	 * 트랜잭션 커밋 전까지 행을 잠가 동시 접수의 차감을 직렬화한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from GatheringScheduleEntity s where s.id = :id")
	fun findByIdForUpdate(@Param("id") id: Long): GatheringScheduleEntity?
}
```

`GatheringMemberMapper.kt` 생성:

```kotlin
package com.org.oneulsogae.infra.gathering.command.mapper

import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity

/** [GatheringMemberEntity] ↔ core 도메인 [GatheringMember] 변환. */
fun GatheringMemberEntity.toDomain(): GatheringMember =
	GatheringMember(
		id = id,
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		userId = userId,
		gender = gender,
		status = status,
		earlyBirdApplied = earlyBirdApplied,
	)

fun GatheringMember.toEntity(): GatheringMemberEntity =
	GatheringMemberEntity(
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		userId = userId,
		gender = gender,
		status = status,
		earlyBirdApplied = earlyBirdApplied,
	)
```

`GatheringMemberAdapter.kt` 생성:

```kotlin
package com.org.oneulsogae.infra.gathering.command.adapter

import com.org.oneulsogae.core.gathering.command.application.port.out.LoadGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.mapper.toDomain
import com.org.oneulsogae.infra.gathering.command.mapper.toEntity
import com.org.oneulsogae.infra.gathering.command.repository.GatheringMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * [GatheringMemberEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * core 참가 접수의 조회([LoadGatheringMemberPort])·저장([SaveGatheringMemberPort]) out-port를 구현한다.
 */
@Component
class GatheringMemberAdapter(
	private val gatheringMemberJpaRepository: GatheringMemberJpaRepository,
) : LoadGatheringMemberPort,
	SaveGatheringMemberPort {

	override fun loadByScheduleIdAndUserId(scheduleId: Long, userId: Long): GatheringMember? =
		gatheringMemberJpaRepository.findByScheduleIdAndUserId(scheduleId, userId)?.toDomain()

	// 신규(id null)는 insert, 기존 행은 로드해 상태·성별·얼리버드 적용 여부를 갱신한다. (gathering_id 등 식별 필드 보존)
	override fun save(member: GatheringMember): GatheringMember {
		val memberId: Long? = member.id
		if (memberId == null) {
			return gatheringMemberJpaRepository.save(member.toEntity()).toDomain()
		}
		val entity: GatheringMemberEntity = gatheringMemberJpaRepository.findById(memberId)
			.orElseThrow { IllegalStateException("모임 참가자를 찾을 수 없습니다: $memberId") }
		entity.status = member.status
		entity.gender = member.gender
		entity.earlyBirdApplied = member.earlyBirdApplied
		return gatheringMemberJpaRepository.save(entity).toDomain()
	}
}
```

`GatheringScheduleAdapter.kt`에 core 포트 구현 추가 — 클래스 선언과 본문을 다음처럼 확장(기존 admin 포트 구현은 유지):

```kotlin
// 추가 import
import com.org.oneulsogae.core.gathering.command.application.port.out.GetJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule

// 클래스 선언에 인터페이스 추가
class GatheringScheduleAdapter(
	private val gatheringScheduleJpaRepository: GatheringScheduleJpaRepository,
) : SaveGatheringSchedulePort,
	LoadGatheringSchedulePort,
	ChangeGatheringScheduleStatusPort,
	GetJoiningSchedulePort,
	SaveJoiningSchedulePort {

	// ... 기존 메서드 유지 ...

	// 참가 접수용: 비관적 쓰기 락으로 잠근 일정을 core 도메인으로 투영한다.
	override fun getForUpdate(scheduleId: Long): JoiningSchedule? =
		gatheringScheduleJpaRepository.findByIdForUpdate(scheduleId)?.let { entity: GatheringScheduleEntity ->
			JoiningSchedule(
				id = checkNotNull(entity.id),
				gatheringId = entity.gatheringId,
				status = entity.status,
				maleFee = entity.maleFee,
				femaleFee = entity.femaleFee,
				maleRemaining = entity.maleRemaining,
				femaleRemaining = entity.femaleRemaining,
				earlyBirdRemaining = entity.earlyBirdRemaining,
				earlyBirdDiscountRate = entity.earlyBirdDiscountRate,
				discountMaleFee = entity.discountMaleFee,
				discountFemaleFee = entity.discountFemaleFee,
			)
		}

	// 접수로 차감된 여분만 반영한다. (같은 트랜잭션에서 잠근 행 — 다른 필드 보존)
	override fun save(schedule: JoiningSchedule) {
		val entity: GatheringScheduleEntity = gatheringScheduleJpaRepository.findById(schedule.id)
			.orElseThrow { IllegalStateException("모임 일정을 찾을 수 없습니다: ${schedule.id}") }
		entity.maleRemaining = schedule.maleRemaining
		entity.femaleRemaining = schedule.femaleRemaining
		entity.earlyBirdRemaining = schedule.earlyBirdRemaining
		gatheringScheduleJpaRepository.save(entity)
	}
}
```

> 주의: `SaveGatheringSchedulePort.save(GatheringSchedule)`(admin)와 `SaveJoiningSchedulePort.save(JoiningSchedule)`(core)는 파라미터 타입이 달라 오버로드로 공존한다.

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add oneulsogae-core oneulsogae-infra
git commit -m "feat(gathering): 일정 참가 접수 유스케이스와 영속성 어댑터 추가"
```

---

### Task 4: payments command — 결제 기록 + 결제완료 서비스

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/PaymentsErrorCode.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/domain/Payment.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/in/CompletePaymentUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/in/command/CompletePaymentCommand.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/in/result/CompletePaymentResult.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/out/SavePaymentPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/CompletePaymentService.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/entity/PaymentEntity.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/repository/PaymentJpaRepository.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/adapter/PaymentAdapter.kt`
- Create: `docs/migration/payments.sql`

**Interfaces:**
- Consumes: Task 3의 `RegisterGatheringMemberUseCase`, user 도메인 `GetUserDetailUseCase.getByUserId(userId): UserDetailView`(`gender: Gender?`)
- Produces:
  - `CompletePaymentUseCase.complete(userId: Long, command: CompletePaymentCommand): CompletePaymentResult`
  - `data class CompletePaymentCommand(val gatheringId: Long, val scheduleId: Long)`
  - `data class CompletePaymentResult(val amount: Int)`
  - `PaymentsErrorCode.ORDERER_GENDER_REQUIRED`

- [ ] **Step 1: 에러 코드 추가**

`PaymentsErrorCode.kt` enum 본문에 추가:

```kotlin
	/** 결제 접수에 필요한 주문자 성별이 프로필에 없음(온보딩 미완료 등). */
	ORDERER_GENDER_REQUIRED("PAYMENTS-002", "주문자 성별을 확인할 수 없습니다. 프로필을 먼저 완성해주세요.", HttpStatus.BAD_REQUEST),
```

- [ ] **Step 2: 도메인·포트 작성**

`command/domain/Payment.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.domain

import com.org.oneulsogae.common.user.Gender

/**
 * 결제 기록(command 도메인 모델). 무검증 접수 단계라 결제수단·PG 검증 정보 없이
 * 누가(userId)·무엇을(gathering/schedule/gender)·얼마에(amount, 서버 확정가) 접수했는지만 남긴다.
 * 참가 상태의 원장은 gathering_members.status 하나로 유지한다(결제 상태 컬럼 없음).
 */
class Payment(
	val id: Long? = null,
	val userId: Long,
	val gatheringId: Long,
	val scheduleId: Long,
	val gender: Gender,
	val amount: Int,
)
```

`port/in/CompletePaymentUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application.port.`in`

import com.org.oneulsogae.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompletePaymentResult

/**
 * 결제완료 접수 인포트(유스케이스). 무검증 접수: 본인 성별을 확정해 참가를 승인대기로 등록하고 결제 기록을 남긴다.
 * 실제 결제수단 검증(PG)은 이후 과제다.
 */
interface CompletePaymentUseCase {

	fun complete(userId: Long, command: CompletePaymentCommand): CompletePaymentResult
}
```

`port/in/command/CompletePaymentCommand.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application.port.`in`.command

/** 결제완료 접수 명령. 성별은 받지 않는다 — 본인 프로필 성별을 서버가 강제한다. */
data class CompletePaymentCommand(
	val gatheringId: Long,
	val scheduleId: Long,
)
```

`port/in/result/CompletePaymentResult.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application.port.`in`.result

/** 결제완료 접수 결과. [amount]는 접수 시점에 서버가 확정한 실결제가다. */
data class CompletePaymentResult(
	val amount: Int,
)
```

`port/out/SavePaymentPort.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application.port.out

import com.org.oneulsogae.core.payments.command.domain.Payment

/** 결제 기록을 저장하는 아웃포트. */
interface SavePaymentPort {

	fun save(payment: Payment): Payment
}
```

- [ ] **Step 3: CompletePaymentService 작성**

`command/application/CompletePaymentService.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.command.application.port.`in`.RegisterGatheringMemberUseCase
import com.org.oneulsogae.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.oneulsogae.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import com.org.oneulsogae.core.payments.command.application.port.`in`.CompletePaymentUseCase
import com.org.oneulsogae.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompletePaymentResult
import com.org.oneulsogae.core.payments.command.application.port.out.SavePaymentPort
import com.org.oneulsogae.core.payments.command.domain.Payment
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CompletePaymentUseCase] 구현.
 * 본인 프로필 성별을 확정하고(요청으로 받지 않음 — 체크아웃에서 보류한 성별 강제),
 * gathering in-port로 참가를 승인대기 등록한 뒤 서버 확정가로 결제 기록을 남긴다.
 * 참가 등록과 결제 기록은 같은 트랜잭션이다(둘 중 하나만 남지 않는다).
 */
@Service
@Transactional
class CompletePaymentService(
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val registerGatheringMemberUseCase: RegisterGatheringMemberUseCase,
	private val savePaymentPort: SavePaymentPort,
) : CompletePaymentUseCase {

	override fun complete(userId: Long, command: CompletePaymentCommand): CompletePaymentResult {
		val gender: Gender = getUserDetailUseCase.getByUserId(userId).gender
			?: throw BusinessException(PaymentsErrorCode.ORDERER_GENDER_REQUIRED)

		val registered: RegisterGatheringMemberResult = registerGatheringMemberUseCase.register(
			RegisterGatheringMemberCommand(
				gatheringId = command.gatheringId,
				scheduleId = command.scheduleId,
				userId = userId,
				gender = gender,
			),
		)

		savePaymentPort.save(
			Payment(
				userId = userId,
				gatheringId = command.gatheringId,
				scheduleId = command.scheduleId,
				gender = gender,
				amount = registered.amount,
			),
		)
		return CompletePaymentResult(amount = registered.amount)
	}
}
```

- [ ] **Step 4: PaymentEntity·리포지토리·어댑터·마이그레이션 작성**

`PaymentEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.payments.command.entity

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
 * 결제 기록 한 건. 무검증 접수 단계라 결제수단·PG 정보 없이 접수 내용(누가·어느 일정·얼마)만 보관한다.
 * 재접수(거절 후 다시 결제완료)마다 새 행이 쌓인다 — (schedule_id, user_id)는 유니크가 아니다.
 * (schedule_id, user_id) 인덱스로 일정별 참가자 목록의 결제금액 조인을 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "payments",
	indexes = [
		// 일정별 참가자의 결제 기록 조회.
		Index(name = "idx_schedule_id_user_id", columnList = "schedule_id, user_id"),
	],
)
class PaymentEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	@Column(name = "schedule_id", nullable = false)
	val scheduleId: Long,

	/** 접수 성별(금액 티어 근거). */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	val gender: Gender,

	/** 서버 확정 실결제가(원). */
	@Column(name = "amount", nullable = false)
	val amount: Int,
) : BaseEntity()
```

`PaymentJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.payments.command.repository

import com.org.oneulsogae.infra.payments.command.entity.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long>
```

`PaymentAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.payments.command.adapter

import com.org.oneulsogae.core.payments.command.application.port.out.SavePaymentPort
import com.org.oneulsogae.core.payments.command.domain.Payment
import com.org.oneulsogae.infra.payments.command.entity.PaymentEntity
import com.org.oneulsogae.infra.payments.command.repository.PaymentJpaRepository
import org.springframework.stereotype.Component

/**
 * [PaymentEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 결제 기록 저장([SavePaymentPort]) out-port를 구현한다.
 */
@Component
class PaymentAdapter(
	private val paymentJpaRepository: PaymentJpaRepository,
) : SavePaymentPort {

	override fun save(payment: Payment): Payment {
		val saved: PaymentEntity = paymentJpaRepository.save(
			PaymentEntity(
				userId = payment.userId,
				gatheringId = payment.gatheringId,
				scheduleId = payment.scheduleId,
				gender = payment.gender,
				amount = payment.amount,
			),
		)
		return Payment(
			id = saved.id,
			userId = saved.userId,
			gatheringId = saved.gatheringId,
			scheduleId = saved.scheduleId,
			gender = saved.gender,
			amount = saved.amount,
		)
	}
}
```

`docs/migration/payments.sql`:

```sql
-- 결제 기록 테이블. 무검증 접수 단계: 결제수단·PG 정보 없이 접수 내용(누가·어느 일정·성별·확정가)만 보관한다.
-- 참가 상태의 원장은 gathering_members.status이며 payments에는 상태 컬럼을 두지 않는다.
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    gathering_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    gender VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    INDEX idx_schedule_id_user_id (schedule_id, user_id)
);
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add oneulsogae-core oneulsogae-infra docs/migration/payments.sql
git commit -m "feat(payments): 결제완료 접수 유스케이스와 결제 기록 테이블 추가"
```

---

### Task 5: 결제완료 API 컨트롤러 + E2E (TDD)

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/request/CompletePaymentRequest.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/response/CompletePaymentResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/PaymentsController.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt`

**Interfaces:**
- Consumes: Task 4의 `CompletePaymentUseCase`
- Produces: `POST /payments/v1/complete` — 요청 `{gatheringId, scheduleId}`, 응답 `{amount}`

- [ ] **Step 1: E2E 실패 테스트 작성**

`PaymentsCompleteE2ETest.kt` 생성:

```kotlin
package com.org.oneulsogae.api.payments

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringMemberEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.oneulsogae.infra.payments.command.entity.PaymentEntity
import com.org.oneulsogae.infra.payments.command.entity.QPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /payments/v1/complete` E2E 테스트.
 *
 * 무검증 결제완료 접수: 본인 프로필 성별을 강제해 참가를 승인대기(PENDING)로 등록하고 결제 기록을 남긴다.
 * - 성별 여분·얼리버드 여분을 접수 시점에 차감한다(PENDING도 정원 포함).
 * - 매진 409(GATHERING-004), 예정 아닌 일정 409(GATHERING-003), 중복 접수 409(GATHERING-005),
 *   일정 없음 404(GATHERING-002), 성별 미확정 400(PAYMENTS-002).
 * - 거절/취소 행은 재접수 시 PENDING으로 되살린다.
 */
class PaymentsCompleteE2ETest : AbstractIntegrationSupport({

	// 성별이 확정된 유저를 저장하고 userId를 돌려준다.
	fun persistUserWithGender(providerId: String, gender: Gender = Gender.MALE): Long {
		val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = gender))
		return userId
	}

	// 모집중 모임 + 일정 1건을 저장하고 (gatheringId, scheduleId)를 돌려준다.
	fun persistGatheringWithSchedule(
		maleRemaining: Int = 4,
		earlyBirdDiscountRate: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
	): Pair<Long, Long> {
		val gatheringId: Long = IntegrationUtil.persist(
			GatheringEntityFixture.create(status = GatheringStatus.RECRUITING),
		).id!!
		val scheduleId: Long = IntegrationUtil.persist(
			GatheringScheduleEntityFixture.create(
				gatheringId = gatheringId,
				maleFee = 10000,
				femaleFee = 8000,
				maleRemaining = maleRemaining,
				earlyBirdDiscountRate = earlyBirdDiscountRate,
				earlyBirdCapacity = earlyBirdCapacity,
				earlyBirdRemaining = earlyBirdRemaining,
				status = status,
			),
		).id!!
		return gatheringId to scheduleId
	}

	fun findMember(scheduleId: Long, userId: Long): GatheringMemberEntity? {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		return IntegrationUtil.getQuery().selectFrom(member)
			.where(member.scheduleId.eq(scheduleId), member.userId.eq(userId))
			.fetchOne()
	}

	fun findSchedule(scheduleId: Long): GatheringScheduleEntity? {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return IntegrationUtil.getQuery().selectFrom(schedule).where(schedule.id.eq(scheduleId)).fetchOne()
	}

	describe("POST /payments/v1/complete") {

		context("성별이 확정된 유저가 얼리버드 유효 일정에 결제완료하면") {
			it("PENDING 참가·결제 기록을 남기고 성별·얼리버드 여분을 차감한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-1", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 2,
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(200)
					body("success", true)
					body("data.amount", 7000)
				}

				val member: GatheringMemberEntity? = findMember(scheduleId, userId)
				member?.status shouldBe GatheringMemberStatus.PENDING
				member?.gender shouldBe Gender.MALE
				member?.earlyBirdApplied shouldBe true

				val schedule: GatheringScheduleEntity? = findSchedule(scheduleId)
				schedule?.maleRemaining shouldBe 3
				schedule?.earlyBirdRemaining shouldBe 1

				val payment: QPaymentEntity = QPaymentEntity.paymentEntity
				val saved: PaymentEntity? = IntegrationUtil.getQuery().selectFrom(payment)
					.where(payment.scheduleId.eq(scheduleId), payment.userId.eq(userId))
					.fetchOne()
				saved?.amount shouldBe 7000
				saved?.gender shouldBe Gender.MALE
			}
		}

		context("해당 성별 여분이 없는 일정에 결제완료하면") {
			it("409 GATHERING-004를 반환하고 아무것도 저장하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-2", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(maleRemaining = 0)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-004")
				}

				findMember(scheduleId, userId) shouldBe null
			}
		}

		context("예정 상태가 아닌 일정에 결제완료하면") {
			it("409 GATHERING-003을 반환한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-3")
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					status = GatheringScheduleStatus.COMPLETED,
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-003")
				}
			}
		}

		context("이미 승인대기 접수가 있는 일정에 다시 결제완료하면") {
			it("409 GATHERING-005를 반환하고 여분을 추가 차감하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-4", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect { status(200) }

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-005")
				}

				findSchedule(scheduleId)?.maleRemaining shouldBe 3
			}
		}

		context("거절된 접수가 있는 유저가 다시 결제완료하면") {
			it("기존 행을 PENDING으로 되살리고 여분을 다시 차감한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-5", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = gatheringId,
						scheduleId = scheduleId,
						userId = userId,
						gender = Gender.MALE,
						status = GatheringMemberStatus.REJECTED,
					),
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(200)
					body("data.amount", 10000)
				}

				findMember(scheduleId, userId)?.status shouldBe GatheringMemberStatus.PENDING
				findSchedule(scheduleId)?.maleRemaining shouldBe 3
			}
		}

		context("성별이 없는 유저가 결제완료하면") {
			it("400 PAYMENTS-002를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "pay-complete-6")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = null))
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(400)
					body("error.code", "PAYMENTS-002")
				}
			}
		}

		context("없는 일정으로 결제완료하면") {
			it("404 GATHERING-002를 반환한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-7")
				val (gatheringId: Long, _) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": 999999}""")
				} expect {
					status(404)
					body("error.code", "GATHERING-002")
				}
			}
		}
	}
})
```

> 확인 완료: 에러 응답 JSON 경로는 `error.code`(기존 `PaymentsCheckoutE2ETest` 검증과 동일).

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCompleteE2ETest"`
Expected: FAIL — 404 (엔드포인트 없음)

- [ ] **Step 3: 요청·응답 DTO 작성**

`request/CompletePaymentRequest.kt`:

```kotlin
package com.org.oneulsogae.api.payments.request

import com.org.oneulsogae.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import jakarta.validation.constraints.NotNull

/** 결제완료 접수 요청. 성별은 받지 않는다(본인 프로필 성별을 서버가 강제). */
data class CompletePaymentRequest(
	@field:NotNull
	val gatheringId: Long?,

	@field:NotNull
	val scheduleId: Long?,
) {

	fun toCommand(): CompletePaymentCommand =
		CompletePaymentCommand(gatheringId = gatheringId!!, scheduleId = scheduleId!!)
}
```

`response/CompletePaymentResponse.kt`:

```kotlin
package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompletePaymentResult

/** 결제완료 접수 응답. [amount]는 서버가 확정한 실결제가다. */
data class CompletePaymentResponse(
	val amount: Int,
) {

	companion object {

		fun of(result: CompletePaymentResult): CompletePaymentResponse =
			CompletePaymentResponse(amount = result.amount)
	}
}
```

- [ ] **Step 4: 컨트롤러에 엔드포인트 추가**

`PaymentsController.kt` — 생성자에 `private val completePaymentUseCase: CompletePaymentUseCase` 주입 추가, 클래스 끝에 메서드 추가:

```kotlin
	/**
	 * 결제완료를 접수한다. 무검증 접수: 본인 프로필 성별을 강제해 참가를 승인대기(PENDING)로 등록하고
	 * 서버 확정가로 결제 기록을 남긴다. 운영자 승인 후 참가(JOINED)로 전환된다.
	 */
	@Operation(
		summary = "결제완료 접수",
		description = "결제 완료를 접수해 참가를 승인대기로 등록한다. 성별 여분·얼리버드 여분을 접수 시점에 차감한다. " +
			"일정 없음 404(GATHERING-002), 판매 중 아님 409(GATHERING-003), 매진 409(GATHERING-004), " +
			"중복 접수 409(GATHERING-005), 성별 미확정 400(PAYMENTS-002).",
	)
	@PostMapping("/complete")
	fun complete(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CompletePaymentRequest,
	): ApiResponse<CompletePaymentResponse> =
		ApiResponse.success(CompletePaymentResponse.of(completePaymentUseCase.complete(user.id, request.toCommand())))
```

추가 import: `CompletePaymentUseCase`, `CompletePaymentRequest`, `CompletePaymentResponse`, `jakarta.validation.Valid`, `PostMapping`, `RequestBody`.

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCompleteE2ETest"`
Expected: PASS (전체 케이스)

- [ ] **Step 6: Commit**

```bash
git add oneulsogae-api
git commit -m "feat(payments): 결제완료 접수 API 추가"
```

---

### Task 6: admin 승인/거절 — 도메인·서비스·infra (TDD)

**Files:**
- Modify: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common/error/AdminErrorCode.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/domain/AdminGatheringMember.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/in/ApproveGatheringMemberUseCase.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/in/RejectGatheringMemberUseCase.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/out/LoadAdminGatheringMemberPort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/out/ChangeGatheringMemberStatusPort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/port/out/RestoreGatheringMemberSeatPort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/ApproveGatheringMemberService.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/command/application/RejectGatheringMemberService.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringMemberAdapter.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/command/adapter/GatheringScheduleAdapter.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/AdminGatheringMemberTest.kt`

**Interfaces:**
- Consumes: Task 1의 `GatheringMemberEntity`, Task 3의 `GatheringMemberJpaRepository`, `GatheringScheduleJpaRepository.findByIdForUpdate`
- Produces:
  - `ApproveGatheringMemberUseCase.approve(scheduleId: Long, memberId: Long)`
  - `RejectGatheringMemberUseCase.reject(scheduleId: Long, memberId: Long)`
  - `AdminGatheringMember(id, scheduleId, gender, status, earlyBirdApplied)` — `fun validateApprovable()`, `fun validateRejectable()`
  - `AdminErrorCode.GATHERING_MEMBER_NOT_FOUND(GATHER-019) / GATHERING_MEMBER_INVALID_STATUS_TRANSITION(GATHER-020)`

- [ ] **Step 1: AdminErrorCode 추가**

`AdminErrorCode.kt`에 GATHER-018 뒤에 추가:

```kotlin
	/** 모임 일정 참가자를 찾지 못함(없거나 해당 일정 소속이 아님). */
	GATHERING_MEMBER_NOT_FOUND("GATHER-019", "모임 참가 신청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 승인대기 상태가 아닌 참가 신청을 승인/거절함. */
	GATHERING_MEMBER_INVALID_STATUS_TRANSITION("GATHER-020", "승인대기 상태의 신청만 승인/거절할 수 있습니다.", HttpStatus.CONFLICT),
```

- [ ] **Step 2: 도메인 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/AdminGatheringMemberTest.kt` 생성:

```kotlin
package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AdminGatheringMemberTest : DescribeSpec({

	fun member(status: GatheringMemberStatus): AdminGatheringMember =
		AdminGatheringMember(
			id = 1L,
			scheduleId = 100L,
			gender = Gender.MALE,
			status = status,
			earlyBirdApplied = false,
		)

	describe("validateApprovable / validateRejectable") {

		context("승인대기 상태면") {
			it("통과한다") {
				member(GatheringMemberStatus.PENDING).validateApprovable()
				member(GatheringMemberStatus.PENDING).validateRejectable()
			}
		}

		context("승인대기가 아닌 상태면") {
			it("GATHERING_MEMBER_INVALID_STATUS_TRANSITION을 던진다") {
				listOf(GatheringMemberStatus.JOINED, GatheringMemberStatus.REJECTED, GatheringMemberStatus.CANCELED)
					.forEach { status: GatheringMemberStatus ->
						val approveException: AdminException = shouldThrow<AdminException> {
							member(status).validateApprovable()
						}
						approveException.errorCode shouldBe AdminErrorCode.GATHERING_MEMBER_INVALID_STATUS_TRANSITION

						val rejectException: AdminException = shouldThrow<AdminException> {
							member(status).validateRejectable()
						}
						rejectException.errorCode shouldBe AdminErrorCode.GATHERING_MEMBER_INVALID_STATUS_TRANSITION
					}
			}
		}
	}
})
```

> 확인 완료: `AdminException(val errorCode: AdminErrorCode, override val message: String = errorCode.message)`.

- [ ] **Step 3: AdminGatheringMember 도메인 구현**

`command/domain/AdminGatheringMember.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender

/**
 * 어드민이 다루는 모임 일정 참가 신청(도메인 모델). 승인/거절 전이 가능 여부를 판정한다.
 * 승인대기(PENDING) 상태만 승인(JOINED)·거절(REJECTED)할 수 있다.
 * [gender]·[earlyBirdApplied]는 거절 시 일정 여분 복원에 쓴다.
 */
class AdminGatheringMember(
	val id: Long,
	val scheduleId: Long,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val earlyBirdApplied: Boolean,
) {

	/** 승인 가능 여부를 검증한다. 승인대기 상태가 아니면 전이 불가. */
	fun validateApprovable() {
		validatePending()
	}

	/** 거절 가능 여부를 검증한다. 승인대기 상태가 아니면 전이 불가. */
	fun validateRejectable() {
		validatePending()
	}

	private fun validatePending() {
		if (status != GatheringMemberStatus.PENDING) {
			throw AdminException(AdminErrorCode.GATHERING_MEMBER_INVALID_STATUS_TRANSITION, "승인대기 상태가 아닙니다: $status")
		}
	}
}
```

> 확인 완료: 상세 메시지 파라미터를 받는 형태가 맞다(`ChangeGatheringScheduleStatusService`와 동일 사용).

- [ ] **Step 4: 도메인 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.AdminGatheringMemberTest"`
Expected: PASS

- [ ] **Step 5: 포트·서비스 작성**

`port/out/LoadAdminGatheringMemberPort.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember

/** 참가 신청 행을 조회하는 아웃포트. */
interface LoadAdminGatheringMemberPort {

	fun loadById(memberId: Long): AdminGatheringMember?
}
```

`port/out/ChangeGatheringMemberStatusPort.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.common.gathering.GatheringMemberStatus

/** 참가 신청 상태를 전이하는 아웃포트. */
interface ChangeGatheringMemberStatusPort {

	fun changeStatus(memberId: Long, status: GatheringMemberStatus)
}
```

`port/out/RestoreGatheringMemberSeatPort.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.common.user.Gender

/** 거절된 접수의 자리를 일정 여분에 복원하는 아웃포트. [earlyBirdApplied]가 true면 얼리버드 여분도 복원한다. */
interface RestoreGatheringMemberSeatPort {

	fun restore(scheduleId: Long, gender: Gender, earlyBirdApplied: Boolean)
}
```

`port/in/ApproveGatheringMemberUseCase.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application.port.`in`

/** 참가 신청 승인 인포트(유스케이스). 승인대기(PENDING) → 참가(JOINED). */
interface ApproveGatheringMemberUseCase {

	fun approve(scheduleId: Long, memberId: Long)
}
```

`port/in/RejectGatheringMemberUseCase.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application.port.`in`

/** 참가 신청 거절 인포트(유스케이스). 승인대기(PENDING) → 거절(REJECTED) + 일정 여분 복원. */
interface RejectGatheringMemberUseCase {

	fun reject(scheduleId: Long, memberId: Long)
}
```

`application/ApproveGatheringMemberService.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.ApproveGatheringMemberUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.out.ChangeGatheringMemberStatusPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadAdminGatheringMemberPort
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ApproveGatheringMemberUseCase] 구현. (명령)
 * 대상 신청을 로드해 없거나 [scheduleId] 일정 소속이 아니면 GATHERING_MEMBER_NOT_FOUND.
 * 전이 가능 여부는 도메인([AdminGatheringMember.validateApprovable])이 판정하고, 통과 시 참가(JOINED)로 전이한다.
 * (승인은 여분을 바꾸지 않는다 — 접수 시점에 이미 차감됨)
 */
@Service
@Transactional
class ApproveGatheringMemberService(
	private val loadAdminGatheringMemberPort: LoadAdminGatheringMemberPort,
	private val changeGatheringMemberStatusPort: ChangeGatheringMemberStatusPort,
) : ApproveGatheringMemberUseCase {

	override fun approve(scheduleId: Long, memberId: Long) {
		val member: AdminGatheringMember = loadAdminGatheringMemberPort.loadById(memberId)
			?.takeIf { it.scheduleId == scheduleId }
			?: throw AdminException(AdminErrorCode.GATHERING_MEMBER_NOT_FOUND, "모임 참가 신청을 찾을 수 없습니다: $memberId")
		member.validateApprovable()
		changeGatheringMemberStatusPort.changeStatus(memberId, GatheringMemberStatus.JOINED)
	}
}
```

`application/RejectGatheringMemberService.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.RejectGatheringMemberUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.out.ChangeGatheringMemberStatusPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadAdminGatheringMemberPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.RestoreGatheringMemberSeatPort
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [RejectGatheringMemberUseCase] 구현. (명령)
 * 대상 신청을 로드해 없거나 [scheduleId] 일정 소속이 아니면 GATHERING_MEMBER_NOT_FOUND.
 * 전이 가능 여부는 도메인([AdminGatheringMember.validateRejectable])이 판정하고,
 * 통과 시 거절(REJECTED)로 전이하며 접수 시 차감한 일정 여분(성별·얼리버드)을 같은 트랜잭션에서 복원한다.
 */
@Service
@Transactional
class RejectGatheringMemberService(
	private val loadAdminGatheringMemberPort: LoadAdminGatheringMemberPort,
	private val changeGatheringMemberStatusPort: ChangeGatheringMemberStatusPort,
	private val restoreGatheringMemberSeatPort: RestoreGatheringMemberSeatPort,
) : RejectGatheringMemberUseCase {

	override fun reject(scheduleId: Long, memberId: Long) {
		val member: AdminGatheringMember = loadAdminGatheringMemberPort.loadById(memberId)
			?.takeIf { it.scheduleId == scheduleId }
			?: throw AdminException(AdminErrorCode.GATHERING_MEMBER_NOT_FOUND, "모임 참가 신청을 찾을 수 없습니다: $memberId")
		member.validateRejectable()
		changeGatheringMemberStatusPort.changeStatus(memberId, GatheringMemberStatus.REJECTED)
		restoreGatheringMemberSeatPort.restore(member.scheduleId, member.gender, member.earlyBirdApplied)
	}
}
```

- [ ] **Step 6: infra 어댑터에 admin 포트 구현 추가**

`GatheringMemberAdapter.kt` — 인터페이스와 메서드 추가(기존 core 포트 구현 유지):

```kotlin
// 추가 import
import com.org.oneulsogae.admin.gathering.command.application.port.out.ChangeGatheringMemberStatusPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadAdminGatheringMemberPort
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember
import com.org.oneulsogae.common.gathering.GatheringMemberStatus

// 클래스 선언에 인터페이스 추가
class GatheringMemberAdapter(
	private val gatheringMemberJpaRepository: GatheringMemberJpaRepository,
) : LoadGatheringMemberPort,
	SaveGatheringMemberPort,
	LoadAdminGatheringMemberPort,
	ChangeGatheringMemberStatusPort {

	// ... 기존 메서드 유지 ...

	// 어드민 승인/거절용: 참가 행을 admin 도메인으로 투영한다.
	override fun loadById(memberId: Long): AdminGatheringMember? =
		gatheringMemberJpaRepository.findById(memberId)
			.map { entity: GatheringMemberEntity ->
				AdminGatheringMember(
					id = checkNotNull(entity.id),
					scheduleId = entity.scheduleId,
					gender = entity.gender,
					status = entity.status,
					earlyBirdApplied = entity.earlyBirdApplied,
				)
			}
			.orElse(null)

	// 기존 행을 로드해 status를 [status]로 전이해 저장한다. (다른 필드 보존)
	override fun changeStatus(memberId: Long, status: GatheringMemberStatus) {
		val entity: GatheringMemberEntity = gatheringMemberJpaRepository.findById(memberId)
			.orElseThrow { IllegalStateException("모임 참가자를 찾을 수 없습니다: $memberId") }
		entity.status = status
		gatheringMemberJpaRepository.save(entity)
	}
}
```

`GatheringScheduleAdapter.kt` — `RestoreGatheringMemberSeatPort` 구현 추가:

```kotlin
// 추가 import
import com.org.oneulsogae.admin.gathering.command.application.port.out.RestoreGatheringMemberSeatPort
import com.org.oneulsogae.common.user.Gender

// 클래스 선언에 RestoreGatheringMemberSeatPort 추가 후 메서드 추가:

	// 거절된 접수의 자리를 복원한다. 접수 차감과 같은 잠금 경로(FOR UPDATE)로 동시 접수와 직렬화한다.
	override fun restore(scheduleId: Long, gender: Gender, earlyBirdApplied: Boolean) {
		val entity: GatheringScheduleEntity = gatheringScheduleJpaRepository.findByIdForUpdate(scheduleId)
			?: throw IllegalStateException("모임 일정을 찾을 수 없습니다: $scheduleId")
		if (gender == Gender.MALE) entity.maleRemaining += 1 else entity.femaleRemaining += 1
		if (earlyBirdApplied) {
			entity.earlyBirdRemaining = (entity.earlyBirdRemaining ?: 0) + 1
		}
		gatheringScheduleJpaRepository.save(entity)
	}
```

- [ ] **Step 7: 컴파일 확인**

Run: `./gradlew :oneulsogae-admin:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add oneulsogae-admin oneulsogae-infra oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering
git commit -m "feat(gathering): 어드민 참가 신청 승인·거절 유스케이스 추가"
```

---

### Task 7: admin 승인/거절 컨트롤러 + E2E (TDD)

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/AdminGatheringMemberController.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/admin/gathering/AdminGatheringMemberE2ETest.kt`

**Interfaces:**
- Consumes: Task 6의 `ApproveGatheringMemberUseCase`, `RejectGatheringMemberUseCase`
- Produces: `POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/approve`, `POST .../reject`

- [ ] **Step 1: E2E 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/admin/gathering/AdminGatheringMemberE2ETest.kt` 생성:

```kotlin
package com.org.oneulsogae.admin.gathering

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringMemberEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringScheduleEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/approve|reject` E2E 테스트.
 *
 * 승인: PENDING → JOINED (여분 변화 없음 — 접수 시 이미 차감).
 * 거절: PENDING → REJECTED + 성별 여분(얼리버드 적용분 포함) 복원.
 * PENDING 아닌 신청 409(GATHER-020), 없는 신청/일정 불일치 404(GATHER-019). ROLE_ADMIN 전용.
 */
class AdminGatheringMemberE2ETest : AbstractIntegrationSupport({

	// 일정(여분 3/4 — 접수 1건 차감 상태) + PENDING 참가 신청을 저장하고 (scheduleId, memberId)를 돌려준다.
	fun persistPendingMember(earlyBirdApplied: Boolean = false): Pair<Long, Long> {
		val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
		val scheduleId: Long = IntegrationUtil.persist(
			GatheringScheduleEntityFixture.create(
				gatheringId = gatheringId,
				maleCapacity = 4,
				maleRemaining = 3,
				earlyBirdDiscountRate = if (earlyBirdApplied) 30 else null,
				earlyBirdCapacity = if (earlyBirdApplied) 2 else null,
				earlyBirdRemaining = if (earlyBirdApplied) 1 else null,
			),
		).id!!
		val memberId: Long = IntegrationUtil.persist(
			GatheringMemberEntityFixture.create(
				gatheringId = gatheringId,
				scheduleId = scheduleId,
				userId = 1000L,
				gender = Gender.MALE,
				status = GatheringMemberStatus.PENDING,
				earlyBirdApplied = earlyBirdApplied,
			),
		).id!!
		return scheduleId to memberId
	}

	fun findMember(memberId: Long): GatheringMemberEntity? {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		return IntegrationUtil.getQuery().selectFrom(member).where(member.id.eq(memberId)).fetchOne()
	}

	fun findSchedule(scheduleId: Long): GatheringScheduleEntity? {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return IntegrationUtil.getQuery().selectFrom(schedule).where(schedule.id.eq(scheduleId)).fetchOne()
	}

	describe("POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/approve") {

		context("승인대기 신청을 승인하면") {
			it("참가(JOINED)로 전이하고 여분은 바꾸지 않는다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("success", true)
				}

				findMember(memberId)?.status shouldBe GatheringMemberStatus.JOINED
				findSchedule(scheduleId)?.maleRemaining shouldBe 3
			}
		}

		context("이미 참가 상태인 신청을 승인하면") {
			it("409 GATHER-020을 반환한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()
				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect { status(200) }

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(409)
					body("error.code", "GATHER-020")
				}
			}
		}

		context("다른 일정의 memberId로 승인하면") {
			it("404 GATHER-019를 반환한다") {
				val (_, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/999999/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(404)
					body("error.code", "GATHER-019")
				}
			}
		}

		context("일반 유저 토큰으로 승인하면") {
			it("403을 반환한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(accessTokenFor(2L))
				} expect {
					status(403)
				}
			}
		}
	}

	describe("POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/reject") {

		context("얼리버드가 적용된 승인대기 신청을 거절하면") {
			it("거절(REJECTED)로 전이하고 성별·얼리버드 여분을 복원한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember(earlyBirdApplied = true)

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/reject") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
				}

				findMember(memberId)?.status shouldBe GatheringMemberStatus.REJECTED
				val schedule: GatheringScheduleEntity? = findSchedule(scheduleId)
				schedule?.maleRemaining shouldBe 4
				schedule?.earlyBirdRemaining shouldBe 2
			}
		}

		context("얼리버드가 아닌 승인대기 신청을 거절하면") {
			it("성별 여분만 복원한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/reject") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
				}

				findMember(memberId)?.status shouldBe GatheringMemberStatus.REJECTED
				findSchedule(scheduleId)?.maleRemaining shouldBe 4
			}
		}
	}
})
```

> 어드민 에러 응답 JSON 경로(`error.code`)와 403 검증 형식은 기존 어드민 E2E(예: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/admin/gathering/` 하위)를 열어 실제 형식에 맞춘다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.admin.gathering.AdminGatheringMemberE2ETest"`
Expected: FAIL — 404 (엔드포인트 없음)

- [ ] **Step 3: 컨트롤러 작성**

`AdminGatheringMemberController.kt` 생성:

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.gathering.command.application.port.`in`.ApproveGatheringMemberUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.RejectGatheringMemberUseCase
import com.org.oneulsogae.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 모임 참가 신청 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 결제완료로 승인대기(PENDING)가 된 신청을 입금 확인 후 승인(JOINED)하거나 거절(REJECTED)한다.
 * - POST /{memberId}/approve: 승인. 없으면 404(GATHER-019), 승인대기 아님 409(GATHER-020).
 * - POST /{memberId}/reject: 거절 + 일정 여분(성별·얼리버드) 복원. 에러는 승인과 동일.
 */
@Tag(name = "어드민 모임 참가 신청", description = "어드민 백오피스 참가 신청 승인·거절. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/gatherings/schedules/{scheduleId}/members")
class AdminGatheringMemberController(
	private val approveGatheringMemberUseCase: ApproveGatheringMemberUseCase,
	private val rejectGatheringMemberUseCase: RejectGatheringMemberUseCase,
) {

	@Operation(
		summary = "참가 신청 승인",
		description = "승인대기(PENDING) 신청을 참가(JOINED)로 전이한다. 여분은 접수 시 이미 차감되어 바뀌지 않는다. " +
			"신청이 없거나 해당 일정 소속이 아니면 404(GATHER-019), 승인대기가 아니면 409(GATHER-020).",
	)
	@PostMapping("/{memberId}/approve")
	fun approve(
		@PathVariable scheduleId: Long,
		@PathVariable memberId: Long,
	): ApiResponse<Unit> {
		approveGatheringMemberUseCase.approve(scheduleId, memberId)
		return ApiResponse.success()
	}

	@Operation(
		summary = "참가 신청 거절",
		description = "승인대기(PENDING) 신청을 거절(REJECTED)로 전이하고 접수 시 차감한 일정 여분(성별·얼리버드)을 복원한다. " +
			"신청이 없거나 해당 일정 소속이 아니면 404(GATHER-019), 승인대기가 아니면 409(GATHER-020).",
	)
	@PostMapping("/{memberId}/reject")
	fun reject(
		@PathVariable scheduleId: Long,
		@PathVariable memberId: Long,
	): ApiResponse<Unit> {
		rejectGatheringMemberUseCase.reject(scheduleId, memberId)
		return ApiResponse.success()
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.admin.gathering.AdminGatheringMemberE2ETest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-api
git commit -m "feat(gathering): 어드민 참가 신청 승인·거절 API 추가"
```

---

### Task 8: admin 일정별 참가자 목록 조회 (TDD)

**Files:**
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/query/dao/GetAdminGatheringMemberDao.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/query/dto/AdminGatheringMemberView.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/query/dto/AdminGatheringMemberViews.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/query/service/GetAdminGatheringMembersService.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/gathering/query/service/port/in/GetAdminGatheringMembersUseCase.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/query/GetAdminGatheringMemberDaoImpl.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminGatheringMemberResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/AdminGatheringMemberController.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/admin/gathering/AdminGatheringMemberListE2ETest.kt`

**Interfaces:**
- Consumes: Task 1의 `GatheringMemberEntity`, Task 4의 `PaymentEntity`, `QUserDetailEntity`(nickname)
- Produces: `GET /admin/v1/gatherings/schedules/{scheduleId}/members` — `[{memberId, userId, nickname, gender, status, amount, appliedAt}]`

- [ ] **Step 1: E2E 실패 테스트 작성**

`AdminGatheringMemberListE2ETest.kt` 생성:

```kotlin
package com.org.oneulsogae.admin.gathering

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringMemberEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.payments.command.entity.PaymentEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize

/**
 * `GET /admin/v1/gatherings/schedules/{scheduleId}/members` E2E 테스트.
 *
 * 일정별 참가 신청 목록(신청 id·유저·닉네임·성별·상태·결제금액·신청 시각)을 신청 순(id 오름차순)으로 반환한다.
 * 결제금액은 (schedule, user)의 최신 결제 기록에서 조인한다(재접수 시 최신 금액). ROLE_ADMIN 전용.
 */
class AdminGatheringMemberListE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/gatherings/schedules/{scheduleId}/members") {

		context("일정에 여러 신청이 있으면") {
			it("신청 순으로 닉네임·상태·결제금액을 담아 반환한다") {
				val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
				val scheduleId: Long = IntegrationUtil.persist(
					GatheringScheduleEntityFixture.create(gatheringId = gatheringId),
				).id!!

				val firstUserId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "admin-list-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = firstUserId, nickname = "첫째"))
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = gatheringId, scheduleId = scheduleId, userId = firstUserId,
						gender = Gender.MALE, status = GatheringMemberStatus.PENDING,
					),
				)
				IntegrationUtil.persist(
					PaymentEntity(userId = firstUserId, gatheringId = gatheringId, scheduleId = scheduleId, gender = Gender.MALE, amount = 10000),
				)

				val secondUserId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "admin-list-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = secondUserId, nickname = "둘째", gender = Gender.FEMALE))
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = gatheringId, scheduleId = scheduleId, userId = secondUserId,
						gender = Gender.FEMALE, status = GatheringMemberStatus.JOINED,
					),
				)
				// 재접수 이력: 과거 8000 → 최신 5600. 최신 금액이 조인되어야 한다.
				IntegrationUtil.persist(
					PaymentEntity(userId = secondUserId, gatheringId = gatheringId, scheduleId = scheduleId, gender = Gender.FEMALE, amount = 8000),
				)
				IntegrationUtil.persist(
					PaymentEntity(userId = secondUserId, gatheringId = gatheringId, scheduleId = scheduleId, gender = Gender.FEMALE, amount = 5600),
				)

				get("/admin/v1/gatherings/schedules/$scheduleId/members") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					body("data.nickname", contains("첫째", "둘째"))
					body("data.status", contains("PENDING", "JOINED"))
					body("data.amount", contains(10000, 5600))
					body("data.gender", contains("MALE", "FEMALE"))
				}
			}
		}

		context("신청이 없는 일정이면") {
			it("빈 목록을 반환한다") {
				val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
				val scheduleId: Long = IntegrationUtil.persist(
					GatheringScheduleEntityFixture.create(gatheringId = gatheringId),
				).id!!

				get("/admin/v1/gatherings/schedules/$scheduleId/members") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}
	}
})
```

> 확인 완료: `UserDetailEntityFixture.create`는 `nickname: String? = "테스트유저"` 파라미터를 가진다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.admin.gathering.AdminGatheringMemberListE2ETest"`
Expected: FAIL — 404 (엔드포인트 없음)

- [ ] **Step 3: admin query dao·dto·service 작성**

`query/dto/AdminGatheringMemberView.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import java.time.LocalDateTime

/**
 * 어드민 일정별 참가 신청 목록의 한 행(read model).
 * [amount]는 (schedule, user)의 최신 결제 기록 금액(기록이 없으면 null — 픽스처 등 예외 상황).
 */
data class AdminGatheringMemberView(
	val memberId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val amount: Int?,
	val appliedAt: LocalDateTime,
)
```

`query/dto/AdminGatheringMemberViews.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.query.dto

/** [AdminGatheringMemberView] 일급 컬렉션. */
data class AdminGatheringMemberViews(
	val values: List<AdminGatheringMemberView>,
)
```

`query/dao/GetAdminGatheringMemberDao.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.query.dao

import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberViews

/** 어드민 일정별 참가 신청 목록 조회 dao. infra가 구현한다. */
interface GetAdminGatheringMemberDao {

	/** [scheduleId] 일정의 참가 신청을 신청 순(id 오름차순)으로 조회한다. */
	fun findByScheduleId(scheduleId: Long): AdminGatheringMemberViews
}
```

`query/service/port/in/GetAdminGatheringMembersUseCase.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.query.service.port.`in`

import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberViews

/** 어드민 일정별 참가 신청 목록 조회 인포트(유스케이스). */
interface GetAdminGatheringMembersUseCase {

	fun getByScheduleId(scheduleId: Long): AdminGatheringMemberViews
}
```

`query/service/GetAdminGatheringMembersService.kt`:

```kotlin
package com.org.oneulsogae.admin.gathering.query.service

import com.org.oneulsogae.admin.gathering.query.dao.GetAdminGatheringMemberDao
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberViews
import com.org.oneulsogae.admin.gathering.query.service.port.`in`.GetAdminGatheringMembersUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetAdminGatheringMembersUseCase] 구현. (조회 전용) */
@Service
@Transactional(readOnly = true)
class GetAdminGatheringMembersService(
	private val getAdminGatheringMemberDao: GetAdminGatheringMemberDao,
) : GetAdminGatheringMembersUseCase {

	override fun getByScheduleId(scheduleId: Long): AdminGatheringMemberViews =
		getAdminGatheringMemberDao.findByScheduleId(scheduleId)
}
```

- [ ] **Step 4: infra daoImpl 작성**

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/gathering/query/GetAdminGatheringMemberDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.gathering.query

import com.org.oneulsogae.admin.gathering.query.dao.GetAdminGatheringMemberDao
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberView
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberViews
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.oneulsogae.infra.payments.command.entity.QPaymentEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminGatheringMemberDao]의 QueryDSL 구현. (조회 전용)
 * 참가 신청을 user_details(닉네임)·payments(결제금액)와 조인해 read model에 직접 투영한다.
 * 결제금액은 (schedule_id, user_id)의 최신(payment id 최대) 기록 한 건만 조인한다(재접수 시 최신 금액).
 * schedule_id 동등 조건은 ux_schedule_id_user_id 유니크 인덱스의 선두 컬럼으로 커버된다.
 */
@Component
class GetAdminGatheringMemberDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminGatheringMemberDao {

	override fun findByScheduleId(scheduleId: Long): AdminGatheringMemberViews {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val payment: QPaymentEntity = QPaymentEntity.paymentEntity
		val latestPayment: QPaymentEntity = QPaymentEntity("latestPayment")

		val views: List<AdminGatheringMemberView> = queryFactory
			.select(
				Projections.constructor(
					AdminGatheringMemberView::class.java,
					member.id,
					member.userId,
					detail.nickname,
					member.gender,
					member.status,
					payment.amount,
					member.createdAt,
				),
			)
			.from(member)
			.leftJoin(detail).on(detail.userId.eq(member.userId))
			.leftJoin(payment).on(
				payment.id.eq(
					JPAExpressions.select(latestPayment.id.max())
						.from(latestPayment)
						.where(
							latestPayment.scheduleId.eq(member.scheduleId),
							latestPayment.userId.eq(member.userId),
						),
				),
			)
			.where(member.scheduleId.eq(scheduleId))
			.orderBy(member.id.asc())
			.fetch()
		return AdminGatheringMemberViews(values = views)
	}
}
```

> `AdminGatheringMemberView.amount`가 `Int?`라 QueryDSL 생성자 투영이 실패하면 `Projections.constructor` 대신 `fetch()` 후 매핑하거나 `Expressions` 캐스팅으로 맞춘다(기존 daoImpl들의 null 허용 컬럼 투영 방식을 우선 확인).

- [ ] **Step 5: 응답 DTO·컨트롤러 GET 추가**

`oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminGatheringMemberResponse.kt`:

```kotlin
package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberView
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import java.time.LocalDateTime

/** 어드민 일정별 참가 신청 목록 응답의 한 행. */
data class AdminGatheringMemberResponse(
	val memberId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val amount: Int?,
	val appliedAt: LocalDateTime,
) {

	companion object {

		fun of(view: AdminGatheringMemberView): AdminGatheringMemberResponse =
			AdminGatheringMemberResponse(
				memberId = view.memberId,
				userId = view.userId,
				nickname = view.nickname,
				gender = view.gender,
				status = view.status,
				amount = view.amount,
				appliedAt = view.appliedAt,
			)
	}
}
```

`AdminGatheringMemberController.kt` — 생성자에 `private val getAdminGatheringMembersUseCase: GetAdminGatheringMembersUseCase` 추가, 메서드 추가:

```kotlin
	@Operation(
		summary = "참가 신청 목록 조회",
		description = "일정의 참가 신청을 신청 순으로 조회한다. 닉네임·성별·상태·결제금액(최신 결제 기록)·신청 시각을 담는다.",
	)
	@GetMapping
	fun list(@PathVariable scheduleId: Long): ApiResponse<List<AdminGatheringMemberResponse>> =
		ApiResponse.success(
			getAdminGatheringMembersUseCase.getByScheduleId(scheduleId).values
				.map { view: AdminGatheringMemberView -> AdminGatheringMemberResponse.of(view) },
		)
```

추가 import: `GetAdminGatheringMembersUseCase`, `AdminGatheringMemberView`, `AdminGatheringMemberResponse`, `GetMapping`.

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.admin.gathering.AdminGatheringMemberListE2ETest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add oneulsogae-admin oneulsogae-infra oneulsogae-api
git commit -m "feat(gathering): 어드민 일정별 참가 신청 목록 조회 API 추가"
```

---

### Task 9: 전체 검증

- [ ] **Step 1: 전체 빌드·테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — 기존 테스트(특히 `PaymentsCheckoutE2ETest`, gathering 관련 E2E) 회귀 없음

- [ ] **Step 2: 회귀 발생 시 수정 후 커밋**

기존 테스트가 깨지면 원인을 파악해 수정한다(예: `GatheringMemberEntity` 생성자 변경에 따른 기존 사용처 — Task 1 시점 기준 사용처 없음이 확인됨).

- [ ] **Step 3: 프론트엔드 안내 메모**

구현 완료 후 사용자에게 안내할 프론트엔드 변경 지점(코드 수정은 하지 않는다):
- 결제 화면에서 `POST /payments/v1/complete`(body: `gatheringId`, `scheduleId`) 호출 → 응답 `data.amount`로 접수 완료 화면 표시.
- 에러 처리: 409 `GATHERING-004`(매진)·`GATHERING-005`(중복 접수), 400 `PAYMENTS-002`(프로필 미완성) 안내 문구.
```
