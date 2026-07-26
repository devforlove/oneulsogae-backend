# 미션 리워드 시스템 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱에 확장 가능한 미션→코인 보상 시스템을 추가한다(첫 미션: 자기소개 100자 이상 작성 시 50코인). 사용자는 미션 목록을 보고 자격이 되면 보상을 수령한다.

**Architecture:** 헥사고날 멀티모듈(common·core·infra·api) + CQRS. `missions`(정의)와 `mission_completions`(UNIQUE user+mission 가드) 테이블. 미션 정의는 query 읽기 모델이고, 명령(claim)도 query in-port로 로드한다(coin `CoinItem` 선례와 동형). 자격 판정은 `MissionType`별 `MissionEvaluator`(코드), 보상·표시·on/off는 DB. claim은 코인 적립 + 완료 가드 INSERT를 한 트랜잭션에서 원자 처리해 유니크 위반이 이중 수령을 롤백한다(coin_item_purchases 패턴 재활용).

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL. 테스트: Kotest(도메인 유닛, mockk 사용 가능) + Testcontainers E2E(`AbstractIntegrationSupport`).

## Global Constraints

- 응답 언어·주석·커밋 메시지 한국어. 코드 식별자 영어.
- `oneulsogae-backend`만 수정.
- 타입 명시(변수·반환·람다 파라미터 타입 생략 금지).
- 도메인 검증·판정 로직은 도메인/평가자에 캡슐화. 서비스 `if…throw` 나열 지양.
- 도메인 간 참조는 **그 도메인 in-port** 주입(타 도메인 out-port·구현체 직접 주입 금지): 미션은 코인 적립을 `AcquireCoinUseCase`(coin in-port), 소개 조회를 `GetUserDetailUseCase`(user in-port)로 한다.
- CQRS: 조회는 `query`(dao·read model·in-port), 명령은 `command`(도메인·포트). 엔티티당 어댑터 하나.
- 미션 정의는 query 읽기 모델 `Mission`. claim도 query in-port `GetMissionUseCase.getById`로 로드(coin `CompleteCoinPurchaseService`가 `GetCoinCheckoutUseCase`로 로드하는 것과 동형).
- 신규 enum: `MissionType { WRITE_INTRODUCTION }`(common), `CoinGetType.MISSION("미션 보상")` 추가.
- 자기소개 자격 임계값: `WriteIntroductionMissionEvaluator.MIN_INTRODUCTION_LENGTH = 100`(trim 후 길이 ≥ 100).
- 에러코드 `MissionErrorCode`: `MISSION_NOT_FOUND("MISSION-001", "미션을 찾을 수 없습니다.", NOT_FOUND)`, `MISSION_NOT_ELIGIBLE("MISSION-002", "아직 미션 조건을 충족하지 않았습니다.", BAD_REQUEST)`, `MISSION_ALREADY_COMPLETED("MISSION-003", "이미 완료한 미션입니다.", CONFLICT)`.
- `/missions/v1/**`는 SecurityConfig permitAll 대상 아님 → 인증 필수(coin과 동일).
- 스펙: `docs/superpowers/specs/2026-07-26-mission-reward-design.md`.

**스펙 대비 단순화 1건**: 스펙은 `MissionCompletion` command 도메인 모델을 언급하나, coin의 가드(`SaveCoinItemPurchasePort.save(userId, itemId)`)처럼 `SaveMissionCompletionPort.save(userId, missionId, rewardedCoin)`이 원시값을 받고 엔티티가 컬럼을 보관하므로 별도 도메인 클래스를 만들지 않는다(YAGNI, coin 선례와 동일).

---

## File Structure

**common**
- `common/mission/MissionType.kt` (신규)
- `common/coin/CoinGetType.kt` (수정 — MISSION 추가)

**core (mission 도메인 신규)**
- query: `mission/query/dto/Mission.kt`·`MissionView.kt`·`MissionViews.kt`; `mission/query/dao/GetMissionDao.kt`·`GetMissionCompletionDao.kt`; `mission/query/service/port/in/GetMissionUseCase.kt`·`GetMissionsUseCase.kt`; `mission/query/service/GetMissionService.kt`·`GetMissionsService.kt`
- 평가자: `mission/application/evaluator/MissionEvaluator.kt`·`WriteIntroductionMissionEvaluator.kt`·`MissionEvaluators.kt`
- command: `mission/command/application/port/out/SaveMissionCompletionPort.kt`; `mission/command/application/port/in/ClaimMissionUseCase.kt`·`port/in/result/ClaimMissionResult.kt`; `mission/command/application/ClaimMissionService.kt`
- `mission/MissionErrorCode.kt`

**infra**
- `mission/command/entity/MissionEntity.kt`·`MissionCompletionEntity.kt`; `mission/command/repository/MissionJpaRepository.kt`·`MissionCompletionJpaRepository.kt`; `mission/command/adapter/MissionCompletionAdapter.kt`; `mission/query/GetMissionDaoImpl.kt`·`GetMissionCompletionDaoImpl.kt`
- `testFixtures/.../fixture/MissionEntityFixture.kt`

**api**
- `mission/MissionController.kt`; `mission/response/MissionResponse.kt`·`ClaimMissionResponse.kt`

**test**
- `domain/mission/WriteIntroductionMissionEvaluatorTest.kt`·`MissionEvaluatorsTest.kt`
- `api/mission/GetMissionsE2ETest.kt`·`ClaimMissionE2ETest.kt`

---

## Task 1: enum 추가 (common)

**Files:**
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/mission/MissionType.kt`
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinGetType.kt`

**Interfaces:**
- Produces: `enum class MissionType { WRITE_INTRODUCTION }`; `CoinGetType.MISSION`.

순수 enum 추가라 유닛 테스트 없음. 컴파일로 확인.

- [ ] **Step 1: MissionType 생성**

Create `MissionType.kt`:

```kotlin
package com.org.oneulsogae.common.mission

/**
 * 미션 유형. 유형별 자격 판정 평가자([com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluator])를 고르는 자연키다.
 * 신규 미션은 값을 추가하고 대응 평가자를 붙여 확장한다.
 */
enum class MissionType {

	/** 자기소개를 일정 길이 이상 작성. */
	WRITE_INTRODUCTION,
}
```

- [ ] **Step 2: CoinGetType에 MISSION 추가**

Modify `CoinGetType.kt` — `REFERRAL("추천 보상"),` 뒤에 추가:

```kotlin
	/** 추천 코드 입력으로 추천인·신규 유저 양쪽에 지급하는 코인. */
	REFERRAL("추천 보상"),

	/** 미션 완료 보상으로 지급하는 코인. */
	MISSION("미션 보상"),
}
```

(기존 마지막 항목 뒤 세미콜론/닫는 중괄호 위치에 맞춰 삽입. `CoinGetType`은 뒤에 멤버 함수가 없어 세미콜론 불필요 — 마지막 항목 콤마 후 `}`.)

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-common:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/mission/MissionType.kt \
        oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinGetType.kt
git commit -m "feat(mission): MissionType enum·CoinGetType.MISSION 추가"
```

---

## Task 2: 미션 read model·에러코드·조회 dao 인터페이스 (core)

**Files:**
- Create: `.../core/mission/query/dto/Mission.kt`·`MissionView.kt`·`MissionViews.kt`
- Create: `.../core/mission/query/dao/GetMissionDao.kt`·`GetMissionCompletionDao.kt`
- Create: `.../core/mission/MissionErrorCode.kt`

**Interfaces:**
- Consumes: `MissionType` (Task 1), `ErrorCode` (기존 `com.org.oneulsogae.core.common.error.ErrorCode`).
- Produces:
  - `data class Mission(id: Long, type: MissionType, rewardCoin: Int, title: String, description: String?, displayOrder: Int)`
  - `data class MissionView(missionId: Long, type: MissionType, title: String, description: String?, rewardCoin: Int, completed: Boolean, eligible: Boolean)`
  - `data class MissionViews(values: List<MissionView>)`
  - `GetMissionDao { fun findActiveMissions(): List<Mission>; fun findActiveById(missionId: Long): Mission? }`
  - `GetMissionCompletionDao { fun findCompletedMissionIds(userId: Long): Set<Long> }`
  - `MissionErrorCode` enum (3 codes).

인터페이스·dto 컴파일 태스크. 유닛 테스트 없음.

- [ ] **Step 1: read model 3개 생성**

Create `Mission.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.dto

import com.org.oneulsogae.common.mission.MissionType

/**
 * 미션 정의(read model). 보상 코인·문구·정렬 순서를 담는다.
 * 자격 판정 로직은 담지 않는다(유형별 평가자가 코드로 판정).
 * 영속성은 [com.org.oneulsogae.infra.mission.command.entity.MissionEntity]가 담당한다.
 */
data class Mission(
	val id: Long,
	val type: MissionType,
	val rewardCoin: Int,
	val title: String,
	val description: String?,
	val displayOrder: Int,
)
```

Create `MissionView.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.dto

import com.org.oneulsogae.common.mission.MissionType

/**
 * 미션 목록 한 건(read model). 정의에 사용자별 상태(완료 여부·수령 가능 여부)를 얹는다.
 * [completed]면 이미 보상을 받은 미션, 아니면 [eligible]로 지금 받을 수 있는지 표시한다.
 */
data class MissionView(
	val missionId: Long,
	val type: MissionType,
	val title: String,
	val description: String?,
	val rewardCoin: Int,
	val completed: Boolean,
	val eligible: Boolean,
)
```

Create `MissionViews.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.dto

/** 미션 목록([MissionView])의 일급 컬렉션. */
data class MissionViews(
	val values: List<MissionView>,
)
```

- [ ] **Step 2: dao 인터페이스 2개 생성**

Create `GetMissionDao.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.dao

import com.org.oneulsogae.core.mission.query.dto.Mission

/** 미션 정의 조회 dao. 활성(active·미삭제) 미션만 반환한다. */
interface GetMissionDao {

	/** 활성 미션 전체를 노출 순서(display_order 오름차순)로 조회한다. */
	fun findActiveMissions(): List<Mission>

	/** 활성 미션 한 건을 id로 조회한다. 없거나 비활성이면 null. */
	fun findActiveById(missionId: Long): Mission?
}
```

Create `GetMissionCompletionDao.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.dao

/** 미션 완료 기록 조회 dao. 목록의 완료 여부 표시에 쓴다. */
interface GetMissionCompletionDao {

	/** 사용자가 완료한 미션 id 집합. */
	fun findCompletedMissionIds(userId: Long): Set<Long>
}
```

- [ ] **Step 3: MissionErrorCode 생성**

Create `MissionErrorCode.kt`:

```kotlin
package com.org.oneulsogae.core.mission

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 미션(mission) 도메인 에러 코드. */
enum class MissionErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 미션을 찾지 못함(없거나 비활성). */
	MISSION_NOT_FOUND("MISSION-001", "미션을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 아직 미션 자격(조건)을 충족하지 않음. */
	MISSION_NOT_ELIGIBLE("MISSION-002", "아직 미션 조건을 충족하지 않았습니다.", HttpStatus.BAD_REQUEST),

	/** 이미 완료·보상 수령한 미션. */
	MISSION_ALREADY_COMPLETED("MISSION-003", "이미 완료한 미션입니다.", HttpStatus.CONFLICT),
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/mission/
git commit -m "feat(mission): 미션 read model·에러코드·조회 dao 인터페이스 추가"
```

---

## Task 3: 미션 평가자 + 유닛 테스트 (core)

**Files:**
- Create: `.../core/mission/application/evaluator/MissionEvaluator.kt`·`WriteIntroductionMissionEvaluator.kt`·`MissionEvaluators.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/mission/WriteIntroductionMissionEvaluatorTest.kt`·`MissionEvaluatorsTest.kt`

**Interfaces:**
- Consumes: `MissionType` (Task 1), `GetUserDetailUseCase.findByUserId(userId): UserDetailView?` with `UserDetailView.introduction: String?` (기존 user in-port).
- Produces:
  - `interface MissionEvaluator { fun supports(type: MissionType): Boolean; fun isEligible(userId: Long): Boolean }`
  - `class WriteIntroductionMissionEvaluator(getUserDetailUseCase)` with `companion object { const val MIN_INTRODUCTION_LENGTH: Int = 100 }`
  - `class MissionEvaluators(evaluators: List<MissionEvaluator>) { fun resolve(type: MissionType): MissionEvaluator }`

- [ ] **Step 1: 평가자 인터페이스·구현·resolver 생성**

Create `MissionEvaluator.kt`:

```kotlin
package com.org.oneulsogae.core.mission.application.evaluator

import com.org.oneulsogae.common.mission.MissionType

/**
 * 미션 유형별 자격 판정기. 신규 미션 유형은 이 인터페이스 구현 빈을 추가해 확장한다.
 * (보상·문구·활성 여부는 DB missions 행이 담고, 자격 조건은 이 평가자가 코드로 판정한다)
 */
interface MissionEvaluator {

	/** 이 평가자가 [type]을 판정할 수 있는지 여부. */
	fun supports(type: MissionType): Boolean

	/** [userId]가 이 미션의 자격(조건)을 충족했는지 여부. */
	fun isEligible(userId: Long): Boolean
}
```

Create `WriteIntroductionMissionEvaluator.kt`:

```kotlin
package com.org.oneulsogae.core.mission.application.evaluator

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Component

/**
 * 자기소개 작성 미션 평가자. 프로필 자기소개가 [MIN_INTRODUCTION_LENGTH]자 이상(앞뒤 공백 제외)이면 자격이 있다.
 * 소개는 user 도메인 in-port([GetUserDetailUseCase])로 조회한다.
 */
@Component
class WriteIntroductionMissionEvaluator(
	private val getUserDetailUseCase: GetUserDetailUseCase,
) : MissionEvaluator {

	override fun supports(type: MissionType): Boolean = type == MissionType.WRITE_INTRODUCTION

	override fun isEligible(userId: Long): Boolean {
		val introduction: String? = getUserDetailUseCase.findByUserId(userId)?.introduction
		return (introduction?.trim()?.length ?: 0) >= MIN_INTRODUCTION_LENGTH
	}

	companion object {
		/** 자격을 얻는 최소 자기소개 길이. */
		const val MIN_INTRODUCTION_LENGTH: Int = 100
	}
}
```

Create `MissionEvaluators.kt`:

```kotlin
package com.org.oneulsogae.core.mission.application.evaluator

import com.org.oneulsogae.common.mission.MissionType
import org.springframework.stereotype.Component

/**
 * 미션 유형에 맞는 [MissionEvaluator]를 고르는 resolver. 주입된 평가자 중 [MissionEvaluator.supports]인 것을 반환한다.
 * 대응 평가자가 없으면 missions 행에 평가자가 붙지 않은 배포 오류이므로 [IllegalStateException]을 던진다(조용히 부적격 처리하지 않는다).
 */
@Component
class MissionEvaluators(
	private val evaluators: List<MissionEvaluator>,
) {

	fun resolve(type: MissionType): MissionEvaluator =
		evaluators.firstOrNull { evaluator: MissionEvaluator -> evaluator.supports(type) }
			?: throw IllegalStateException("미션 유형에 대응하는 평가자가 없습니다: $type")
}
```

- [ ] **Step 2: 유닛 테스트 작성**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/mission/WriteIntroductionMissionEvaluatorTest.kt`:

```kotlin
package com.org.oneulsogae.domain.mission

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.application.evaluator.WriteIntroductionMissionEvaluator
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * [WriteIntroductionMissionEvaluator] 유닛 테스트.
 * 자기소개 길이(앞뒤 공백 제외) 100자 임계값과 지원 유형을 검증한다.
 */
class WriteIntroductionMissionEvaluatorTest : DescribeSpec({

	val getUserDetailUseCase: GetUserDetailUseCase = mockk()
	val evaluator = WriteIntroductionMissionEvaluator(getUserDetailUseCase)

	describe("isEligible") {
		it("소개가 100자 이상이면 자격이 있다") {
			val view: UserDetailView = mockk()
			every { view.introduction } returns "가".repeat(100)
			every { getUserDetailUseCase.findByUserId(1L) } returns view

			evaluator.isEligible(1L) shouldBe true
		}

		it("소개가 99자면 자격이 없다") {
			val view: UserDetailView = mockk()
			every { view.introduction } returns "가".repeat(99)
			every { getUserDetailUseCase.findByUserId(1L) } returns view

			evaluator.isEligible(1L) shouldBe false
		}

		it("앞뒤 공백을 뺀 길이로 판정한다 (공백 패딩은 자격 없음)") {
			val view: UserDetailView = mockk()
			every { view.introduction } returns "  " + "가".repeat(50) + "   "
			every { getUserDetailUseCase.findByUserId(1L) } returns view

			evaluator.isEligible(1L) shouldBe false
		}

		it("프로필이 없거나 소개가 null이면 자격이 없다") {
			every { getUserDetailUseCase.findByUserId(1L) } returns null

			evaluator.isEligible(1L) shouldBe false
		}
	}

	describe("supports") {
		it("WRITE_INTRODUCTION만 지원한다") {
			evaluator.supports(MissionType.WRITE_INTRODUCTION) shouldBe true
		}
	}
})
```

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/mission/MissionEvaluatorsTest.kt`:

```kotlin
package com.org.oneulsogae.domain.mission

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluator
import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluators
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MissionEvaluators] resolver 유닛 테스트.
 * 지원 평가자 선택과, 대응 평가자가 없을 때의 IllegalStateException을 검증한다.
 */
class MissionEvaluatorsTest : DescribeSpec({

	val supporting = object : MissionEvaluator {
		override fun supports(type: MissionType): Boolean = type == MissionType.WRITE_INTRODUCTION
		override fun isEligible(userId: Long): Boolean = true
	}

	describe("resolve") {
		it("유형을 지원하는 평가자를 반환한다") {
			MissionEvaluators(listOf(supporting)).resolve(MissionType.WRITE_INTRODUCTION) shouldBe supporting
		}

		it("대응 평가자가 없으면 IllegalStateException을 던진다") {
			shouldThrow<IllegalStateException> {
				MissionEvaluators(emptyList()).resolve(MissionType.WRITE_INTRODUCTION)
			}
		}
	}
})
```

- [ ] **Step 3: 테스트 실행(실패 확인)**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.mission.*"`
Expected: FAIL — 평가자 클래스 미해결(compile error) 전이면 통과할 수 없다. (Step 1 코드가 이미 있으면 GREEN. 순서상 Step 1 → Step 2 → 실행이면 바로 GREEN이므로, TDD 흐름을 원하면 Step 1 전에 테스트를 먼저 두고 RED를 확인해도 된다. 최종 커밋 코드가 GREEN이면 충분.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.mission.*"`
Expected: PASS (7 assertions).

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/mission/application/ \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/mission/
git commit -m "feat(mission): 미션 평가자(자기소개 100자)·resolver 추가"
```

---

## Task 4: 미션 조회 서비스 (core query)

**Files:**
- Create: `.../core/mission/query/service/port/in/GetMissionUseCase.kt`·`GetMissionsUseCase.kt`
- Create: `.../core/mission/query/service/GetMissionService.kt`·`GetMissionsService.kt`

**Interfaces:**
- Consumes: `GetMissionDao`·`GetMissionCompletionDao` (Task 2), `Mission`·`MissionView`·`MissionViews` (Task 2), `MissionEvaluators.resolve` (Task 3), `MissionErrorCode.MISSION_NOT_FOUND` (Task 2), `BusinessException` (기존).
- Produces:
  - `GetMissionUseCase { fun getById(missionId: Long): Mission }`
  - `GetMissionsUseCase { fun getMissions(userId: Long): MissionViews }`

컴파일 태스크. E2E(Task 8·9)가 실사용 검증.

- [ ] **Step 1: in-port 2개 생성**

Create `GetMissionUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.service.port.`in`

import com.org.oneulsogae.core.mission.query.dto.Mission

/** 활성 미션 정의 단건 조회 인포트. (claim이 미션 정의를 로드하는 데 쓴다) */
interface GetMissionUseCase {

	/** 활성 미션을 id로 조회한다. 없거나 비활성이면 MISSION_NOT_FOUND를 던진다. */
	fun getById(missionId: Long): Mission
}
```

Create `GetMissionsUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.service.port.`in`

import com.org.oneulsogae.core.mission.query.dto.MissionViews

/** 사용자별 미션 목록(정의 + 완료·수령가능 상태) 조회 인포트. */
interface GetMissionsUseCase {

	fun getMissions(userId: Long): MissionViews
}
```

- [ ] **Step 2: 서비스 2개 생성**

Create `GetMissionService.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.mission.MissionErrorCode
import com.org.oneulsogae.core.mission.query.dao.GetMissionDao
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetMissionUseCase] 구현. 활성 미션을 조회하고 없으면 [MissionErrorCode.MISSION_NOT_FOUND]. */
@Service
@Transactional(readOnly = true)
class GetMissionService(
	private val getMissionDao: GetMissionDao,
) : GetMissionUseCase {

	override fun getById(missionId: Long): Mission =
		getMissionDao.findActiveById(missionId)
			?: throw BusinessException(MissionErrorCode.MISSION_NOT_FOUND)
}
```

Create `GetMissionsService.kt`:

```kotlin
package com.org.oneulsogae.core.mission.query.service

import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluators
import com.org.oneulsogae.core.mission.query.dao.GetMissionCompletionDao
import com.org.oneulsogae.core.mission.query.dao.GetMissionDao
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.core.mission.query.dto.MissionView
import com.org.oneulsogae.core.mission.query.dto.MissionViews
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetMissionsUseCase] 구현. (조회 전용)
 * 활성 미션 목록에 사용자별 상태를 얹는다: 완료 가드가 있으면 completed, 미완료면 평가자로 eligible을 판정한다.
 */
@Service
@Transactional(readOnly = true)
class GetMissionsService(
	private val getMissionDao: GetMissionDao,
	private val getMissionCompletionDao: GetMissionCompletionDao,
	private val missionEvaluators: MissionEvaluators,
) : GetMissionsUseCase {

	override fun getMissions(userId: Long): MissionViews {
		val completedMissionIds: Set<Long> = getMissionCompletionDao.findCompletedMissionIds(userId)
		return MissionViews(
			getMissionDao.findActiveMissions().map { mission: Mission ->
				val completed: Boolean = mission.id in completedMissionIds
				MissionView(
					missionId = mission.id,
					type = mission.type,
					title = mission.title,
					description = mission.description,
					rewardCoin = mission.rewardCoin,
					completed = completed,
					// 이미 완료면 자격 판정은 무의미하므로 평가자를 호출하지 않는다(미완료만 판정).
					eligible = if (completed) false else missionEvaluators.resolve(mission.type).isEligible(userId),
				)
			},
		)
	}
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/mission/query/service/
git commit -m "feat(mission): 미션 목록·단건 조회 서비스 추가"
```

---

## Task 5: 미션 수령(claim) 원자 처리 (core command)

**Files:**
- Create: `.../core/mission/command/application/port/out/SaveMissionCompletionPort.kt`
- Create: `.../core/mission/command/application/port/in/ClaimMissionUseCase.kt`·`port/in/result/ClaimMissionResult.kt`
- Create: `.../core/mission/command/application/ClaimMissionService.kt`

**Interfaces:**
- Consumes: `GetMissionUseCase.getById` (Task 4), `MissionEvaluators.resolve` (Task 3), `AcquireCoinUseCase.acquire(userId, AcquireCoinCommand(amount, coinType)): CoinBalance` + `AcquireCoinCommand` + `CoinGetType.MISSION` (기존/Task 1) + `CoinBalance.balance`, `MissionErrorCode` (Task 2), `Mission` (Task 2), `BusinessException`, `DataIntegrityViolationException`.
- Produces:
  - `SaveMissionCompletionPort { fun save(userId: Long, missionId: Long, rewardedCoin: Int) }`
  - `ClaimMissionUseCase { fun claim(userId: Long, missionId: Long): ClaimMissionResult }`
  - `data class ClaimMissionResult(rewardedCoin: Int, balance: Int)`

컴파일 태스크. E2E(Task 9)가 실사용 검증.

- [ ] **Step 1: out-port·in-port·result 생성**

Create `SaveMissionCompletionPort.kt`:

```kotlin
package com.org.oneulsogae.core.mission.command.application.port.out

/**
 * 미션 완료 가드 기록 저장 out-port.
 * (user_id, mission_id) 유니크라 이미 완료했으면 저장 시 DataIntegrityViolationException이 발생한다 —
 * 이 위반이 이중 수령을 원자적으로 막는 최종 방어선이다.
 */
interface SaveMissionCompletionPort {

	fun save(userId: Long, missionId: Long, rewardedCoin: Int)
}
```

Create `ClaimMissionUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.mission.command.application.port.`in`

import com.org.oneulsogae.core.mission.command.application.port.`in`.result.ClaimMissionResult

/** 미션 보상 수령 인포트. 자격을 재검증하고 코인 적립 + 완료 기록을 원자적으로 처리한다. */
interface ClaimMissionUseCase {

	fun claim(userId: Long, missionId: Long): ClaimMissionResult
}
```

Create `ClaimMissionResult.kt`:

```kotlin
package com.org.oneulsogae.core.mission.command.application.port.`in`.result

/** 미션 보상 수령 결과 — 지급 코인과 적립 후 잔액. */
data class ClaimMissionResult(
	val rewardedCoin: Int,
	val balance: Int,
)
```

- [ ] **Step 2: ClaimMissionService 생성**

Create `ClaimMissionService.kt`:

```kotlin
package com.org.oneulsogae.core.mission.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.mission.MissionErrorCode
import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluators
import com.org.oneulsogae.core.mission.command.application.port.`in`.ClaimMissionUseCase
import com.org.oneulsogae.core.mission.command.application.port.`in`.result.ClaimMissionResult
import com.org.oneulsogae.core.mission.command.application.port.out.SaveMissionCompletionPort
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionUseCase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ClaimMissionUseCase] 구현.
 * ① 활성 미션 로드(없으면 404) ② 평가자로 자격 재검증(부적격이면 400 — 클라 표시와 무관하게 서버가 실제 상태로 판정)
 * ③ 코인 적립([AcquireCoinUseCase])과 완료 가드([SaveMissionCompletionPort])를 **한 트랜잭션**에서 처리한다.
 * (user_id, mission_id) 유니크 위반(이미 수령)이면 트랜잭션이 롤백돼 적립도 취소되고 409([MissionErrorCode.MISSION_ALREADY_COMPLETED])로 매핑한다.
 * (이 원자성이 이중 수령을 원천 차단한다 — coin 패키지의 AcquirePurchasedCoinService와 동형)
 */
@Service
class ClaimMissionService(
	private val getMissionUseCase: GetMissionUseCase,
	private val missionEvaluators: MissionEvaluators,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val saveMissionCompletionPort: SaveMissionCompletionPort,
) : ClaimMissionUseCase {

	@Transactional
	override fun claim(userId: Long, missionId: Long): ClaimMissionResult {
		val mission: Mission = getMissionUseCase.getById(missionId)

		if (!missionEvaluators.resolve(mission.type).isEligible(userId)) {
			throw BusinessException(MissionErrorCode.MISSION_NOT_ELIGIBLE)
		}

		val balance: CoinBalance = acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(amount = mission.rewardCoin, coinType = CoinGetType.MISSION),
		)
		try {
			saveMissionCompletionPort.save(userId, missionId, mission.rewardCoin)
		} catch (_: DataIntegrityViolationException) {
			// 자격 검증과 적립 사이 경합으로 완료 가드가 먼저 들어간 경우. 트랜잭션 롤백 → 적립 취소.
			throw BusinessException(MissionErrorCode.MISSION_ALREADY_COMPLETED)
		}

		return ClaimMissionResult(rewardedCoin = mission.rewardCoin, balance = balance.balance)
	}
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/mission/command/
git commit -m "feat(mission): 보상 수령(claim) 원자 적립+완료가드 서비스 추가"
```

---

## Task 6: 미션 영속성 (엔티티·어댑터·dao 구현·픽스처, infra)

**Files:**
- Create: `.../infra/mission/command/entity/MissionEntity.kt`·`MissionCompletionEntity.kt`
- Create: `.../infra/mission/command/repository/MissionJpaRepository.kt`·`MissionCompletionJpaRepository.kt`
- Create: `.../infra/mission/command/adapter/MissionCompletionAdapter.kt`
- Create: `.../infra/mission/query/GetMissionDaoImpl.kt`·`GetMissionCompletionDaoImpl.kt`
- Create: `.../infra/src/testFixtures/.../fixture/MissionEntityFixture.kt`

**Interfaces:**
- Consumes: `MissionType` (Task 1), `Mission` (Task 2), `GetMissionDao`·`GetMissionCompletionDao` (Task 2), `SaveMissionCompletionPort` (Task 5), `BaseEntity` (기존).
- Produces: `MissionEntity(type, rewardCoin, title, description=null, active=true, displayOrder=0)`; `MissionCompletionEntity(userId, missionId, rewardedCoin)` UNIQUE(user_id, mission_id); `MissionEntityFixture.create(...)`.

DB·어댑터 태스크. 유닛 테스트 없음. Task 8·9 E2E가 검증. 컴파일로 확인.

- [ ] **Step 1: 엔티티 2개 생성**

Create `MissionEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.mission.command.entity

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 미션 정의 영속성 엔티티. 유형(type)·보상 코인·문구·활성 여부·노출 순서를 보관한다.
 * 자격 판정 로직은 두지 않는다(유형별 평가자가 코드로 판정). 삭제는 soft delete(deleted_at).
 * (type) 유니크 — 한 유형당 한 미션.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "missions",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_missions_type", columnNames = ["type"]),
	],
)
class MissionEntity(
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	val type: MissionType,

	/** 완료 시 지급 코인. */
	@Column(name = "reward_coin", nullable = false)
	var rewardCoin: Int,

	/** 목록 제목. */
	@Column(name = "title", nullable = false, columnDefinition = "varchar(100)")
	var title: String,

	/** 목록 설명. */
	@Column(name = "description", columnDefinition = "varchar(255)")
	var description: String? = null,

	/** 노출·수령 가능 여부. */
	@Column(name = "active", nullable = false)
	var active: Boolean = true,

	/** 목록 정렬(오름차순). */
	@Column(name = "display_order", nullable = false)
	var displayOrder: Int = 0,
) : BaseEntity()
```

Create `MissionCompletionEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.mission.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 미션 완료 기록(가드). 미션 보상을 실제 수령한 시점에 저장한다.
 * (user_id, mission_id) 유니크가 이중 수령을 원자적으로 막는다.
 * [rewardedCoin]은 수령 시점 보상 코인의 스냅샷이다(정의가 나중에 바뀌어도 이력 보존).
 */
@Entity
@Table(
	name = "mission_completions",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_mission_completions_user_mission", columnNames = ["user_id", "mission_id"]),
	],
)
class MissionCompletionEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "mission_id", nullable = false)
	val missionId: Long,

	@Column(name = "rewarded_coin", nullable = false)
	val rewardedCoin: Int,
) : BaseEntity()
```

- [ ] **Step 2: 리포지토리 2개 생성**

Create `MissionJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.mission.command.repository

import com.org.oneulsogae.infra.mission.command.entity.MissionEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 미션 정의 리포지토리. 조회 포트는 query dao 구현이 담당한다. */
interface MissionJpaRepository : JpaRepository<MissionEntity, Long>
```

Create `MissionCompletionJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.mission.command.repository

import com.org.oneulsogae.infra.mission.command.entity.MissionCompletionEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 미션 완료 기록 리포지토리. 도메인 포트는 어댑터가 구현한다. */
interface MissionCompletionJpaRepository : JpaRepository<MissionCompletionEntity, Long>
```

- [ ] **Step 3: 완료 저장 어댑터 생성**

Create `MissionCompletionAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.mission.command.adapter

import com.org.oneulsogae.core.mission.command.application.port.out.SaveMissionCompletionPort
import com.org.oneulsogae.infra.mission.command.entity.MissionCompletionEntity
import com.org.oneulsogae.infra.mission.command.repository.MissionCompletionJpaRepository
import org.springframework.stereotype.Component

/**
 * [MissionCompletionEntity] command 영속성 어댑터. 완료 가드 저장([SaveMissionCompletionPort])을 구현한다.
 * (user_id, mission_id) 유니크 위반은 saveAndFlush 시점에 DataIntegrityViolationException으로 즉시 표면화한다(호출 서비스가 잡아 409 매핑).
 */
@Component
class MissionCompletionAdapter(
	private val missionCompletionJpaRepository: MissionCompletionJpaRepository,
) : SaveMissionCompletionPort {

	override fun save(userId: Long, missionId: Long, rewardedCoin: Int) {
		missionCompletionJpaRepository.saveAndFlush(
			MissionCompletionEntity(userId = userId, missionId = missionId, rewardedCoin = rewardedCoin),
		)
	}
}
```

- [ ] **Step 4: 조회 dao 구현 2개 생성 (QueryDSL)**

Create `GetMissionDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.mission.query

import com.org.oneulsogae.core.mission.query.dao.GetMissionDao
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.infra.mission.command.entity.QMissionEntity
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetMissionDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [Mission] read model로 바로 투영한다. @SQLRestriction으로 soft-delete 행은 제외되고, active 조건을 where에서 건다.
 */
@Component
class GetMissionDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMissionDao {

	override fun findActiveMissions(): List<Mission> {
		val mission: QMissionEntity = QMissionEntity.missionEntity
		return queryFactory
			.select(projection(mission))
			.from(mission)
			.where(mission.active.isTrue)
			.orderBy(mission.displayOrder.asc())
			.fetch()
	}

	override fun findActiveById(missionId: Long): Mission? {
		val mission: QMissionEntity = QMissionEntity.missionEntity
		return queryFactory
			.select(projection(mission))
			.from(mission)
			.where(mission.id.eq(missionId), mission.active.isTrue)
			.fetchOne()
	}

	private fun projection(mission: QMissionEntity): Expression<Mission> =
		Projections.constructor(
			Mission::class.java,
			mission.id,
			mission.type,
			mission.rewardCoin,
			mission.title,
			mission.description,
			mission.displayOrder,
		)
}
```

Create `GetMissionCompletionDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.mission.query

import com.org.oneulsogae.core.mission.query.dao.GetMissionCompletionDao
import com.org.oneulsogae.infra.mission.command.entity.QMissionCompletionEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/** [GetMissionCompletionDao]의 QueryDSL 구현. 사용자의 완료 미션 id 집합을 조회한다. */
@Component
class GetMissionCompletionDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMissionCompletionDao {

	override fun findCompletedMissionIds(userId: Long): Set<Long> {
		val completion: QMissionCompletionEntity = QMissionCompletionEntity.missionCompletionEntity
		return queryFactory
			.select(completion.missionId)
			.from(completion)
			.where(completion.userId.eq(userId))
			.fetch()
			.toSet()
	}
}
```

- [ ] **Step 5: 픽스처 생성**

Create `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/MissionEntityFixture.kt`:

```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.infra.mission.command.entity.MissionEntity

/**
 * [MissionEntity] 테스트 픽스처. 기본은 첫 미션(자기소개 100자 → 50코인, 활성)이다.
 */
object MissionEntityFixture {

	fun create(
		type: MissionType = MissionType.WRITE_INTRODUCTION,
		rewardCoin: Int = 50,
		title: String = "자기소개 작성",
		description: String? = "자기소개를 100자 이상 작성하고 50코인을 받으세요",
		active: Boolean = true,
		displayOrder: Int = 0,
	): MissionEntity =
		MissionEntity(
			type = type,
			rewardCoin = rewardCoin,
			title = title,
			description = description,
			active = active,
			displayOrder = displayOrder,
		)
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-infra:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/mission/ \
        oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/MissionEntityFixture.kt
git commit -m "feat(mission): 미션·완료기록 엔티티·어댑터·조회 dao·픽스처 추가"
```

---

## Task 7: 미션 API (api)

**Files:**
- Create: `.../api/mission/MissionController.kt`
- Create: `.../api/mission/response/MissionResponse.kt`·`ClaimMissionResponse.kt`

**Interfaces:**
- Consumes: `GetMissionsUseCase.getMissions(userId): MissionViews` (Task 4), `ClaimMissionUseCase.claim(userId, missionId): ClaimMissionResult` (Task 5), `MissionView`·`MissionViews` (Task 2), `ClaimMissionResult` (Task 5), `MissionType` (Task 1), `AuthUser`/`@LoginUser`/`ApiResponse` (기존).
- Produces: `GET /missions/v1`, `POST /missions/v1/{missionId}/claim`.

컴파일 태스크. E2E(Task 8·9)가 검증.

- [ ] **Step 1: 응답 DTO 2개 생성**

Create `MissionResponse.kt`:

```kotlin
package com.org.oneulsogae.api.mission.response

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.query.dto.MissionView
import com.org.oneulsogae.core.mission.query.dto.MissionViews

/**
 * 미션 목록 한 건 응답. [completed]면 이미 보상을 받은 미션이고, 미완료면 [eligible]로 지금 수령 가능한지 표시한다.
 */
data class MissionResponse(
	val missionId: Long,
	val type: MissionType,
	val title: String,
	val description: String?,
	val rewardCoin: Int,
	val completed: Boolean,
	val eligible: Boolean,
) {
	companion object {

		fun of(view: MissionView): MissionResponse =
			MissionResponse(
				missionId = view.missionId,
				type = view.type,
				title = view.title,
				description = view.description,
				rewardCoin = view.rewardCoin,
				completed = view.completed,
				eligible = view.eligible,
			)

		fun listOf(views: MissionViews): List<MissionResponse> =
			views.values.map { view: MissionView -> of(view) }
	}
}
```

Create `ClaimMissionResponse.kt`:

```kotlin
package com.org.oneulsogae.api.mission.response

import com.org.oneulsogae.core.mission.command.application.port.`in`.result.ClaimMissionResult

/** 미션 보상 수령 응답 — 지급 코인과 적립 후 잔액. */
data class ClaimMissionResponse(
	val rewardedCoin: Int,
	val balance: Int,
) {
	companion object {

		fun of(result: ClaimMissionResult): ClaimMissionResponse =
			ClaimMissionResponse(rewardedCoin = result.rewardedCoin, balance = result.balance)
	}
}
```

- [ ] **Step 2: 컨트롤러 생성**

Create `MissionController.kt`:

```kotlin
package com.org.oneulsogae.api.mission

import com.org.oneulsogae.api.mission.response.ClaimMissionResponse
import com.org.oneulsogae.api.mission.response.MissionResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.mission.command.application.port.`in`.ClaimMissionUseCase
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 미션 엔드포인트. (인증 필요)
 * - GET /missions/v1: 활성 미션 목록을 사용자별 상태(completed·eligible)와 함께 조회한다.
 * - POST /missions/v1/{missionId}/claim: 자격이 되면 보상 코인을 수령한다. (회원당 미션 1회)
 */
@Tag(name = "미션", description = "미션 목록 조회·보상 수령 엔드포인트 (인증 필요)")
@RestController
@RequestMapping("/missions/v1")
class MissionController(
	private val getMissionsUseCase: GetMissionsUseCase,
	private val claimMissionUseCase: ClaimMissionUseCase,
) {

	/** 활성 미션 목록을 조회한다. 각 미션에 completed(수령 완료)·eligible(지금 수령 가능) 상태가 실린다. */
	@Operation(
		summary = "미션 목록 조회",
		description = "활성 미션을 노출 순서대로 내려준다. 각 항목은 missionId·type·title·description·rewardCoin과, 요청 사용자의 completed(이미 수령)·eligible(미완료이면서 지금 자격 충족) 상태를 담는다.",
	)
	@GetMapping
	fun getMissions(
		@LoginUser user: AuthUser,
	): ApiResponse<List<MissionResponse>> =
		ApiResponse.success(MissionResponse.listOf(getMissionsUseCase.getMissions(user.id)))

	/** 미션 보상을 수령한다. 서버가 자격을 재검증하고 코인을 적립한다. */
	@Operation(
		summary = "미션 보상 수령",
		description = "자격을 서버가 재검증한 뒤 보상 코인을 적립한다. 없거나 비활성 미션이면 404(MISSION-001), 자격 미충족이면 400(MISSION-002), 이미 수령했으면 409(MISSION-003). 성공 시 지급 코인(rewardedCoin)과 적립 후 잔액(balance)을 반환한다.",
	)
	@PostMapping("/{missionId}/claim")
	fun claim(
		@LoginUser user: AuthUser,
		@PathVariable("missionId") missionId: Long,
	): ApiResponse<ClaimMissionResponse> =
		ApiResponse.success(ClaimMissionResponse.of(claimMissionUseCase.claim(user.id, missionId)))
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/mission/
git commit -m "feat(mission): 미션 목록 조회·보상 수령 API 추가"
```

---

## Task 8: 미션 목록 조회 E2E

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/mission/GetMissionsE2ETest.kt`

**Interfaces:**
- Consumes: `GET /missions/v1`, `MissionEntityFixture` (Task 6), `UserDetailEntityFixture`·`UserEntityFixture` (기존), `IntegrationUtil`, `AbstractIntegrationSupport`.

`UserDetailEntityFixture.create(userId = ..., introduction = ...)`는 `introduction: String? = null` 인자를 이미 지원한다(확인 완료 — 추가 작업 불필요).

- [ ] **Step 1: E2E 작성**

Create `GetMissionsE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.mission

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MissionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.mission.command.entity.QMissionCompletionEntity
import com.org.oneulsogae.infra.mission.command.entity.QMissionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers

/**
 * `GET /missions/v1` E2E 테스트.
 * 자기소개 길이에 따른 eligible 표시와, 활성 미션 목록 노출을 검증한다.
 */
class GetMissionsE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QMissionCompletionEntity.missionCompletionEntity)
		IntegrationUtil.deleteAll(QMissionEntity.missionEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}

	describe("GET /missions/v1") {

		context("자기소개가 100자 미만인 사용자가 조회하면") {
			it("자기소개 미션이 eligible=false로 내려간다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-list-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(50)))
				IntegrationUtil.persist(MissionEntityFixture.create())

				get("/missions/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data[0].type", "WRITE_INTRODUCTION")
					body("data[0].rewardCoin", 50)
					body("data[0].completed", false)
					body("data[0].eligible", false)
				}
			}
		}

		context("자기소개가 100자 이상인 사용자가 조회하면") {
			it("자기소개 미션이 eligible=true로 내려간다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-list-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				IntegrationUtil.persist(MissionEntityFixture.create())

				get("/missions/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data[0].eligible", true)
					body("data[0].completed", false)
				}
			}
		}

		context("비활성 미션은") {
			it("목록에서 빠진다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-list-3")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				IntegrationUtil.persist(MissionEntityFixture.create(active = false))

				get("/missions/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 0)
				}
			}
		}
	}
})
```

주의: `body(path, matcher)`는 필요 시 `org.hamcrest.Matchers`를 쓴다(예: 타입/값 매칭). 위 예시는 `body(path, value)` 오버로드(정수·문자열·불리언 직접 비교)만 사용하므로 Matchers import는 실제로 안 쓰이면 제거한다. E2E DSL(`get`/`expect`/`bearer`/`status`/`body`)은 기존 `RestAssuredDsl`과 동일.

- [ ] **Step 2: 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.mission.GetMissionsE2ETest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/mission/GetMissionsE2ETest.kt
# (픽스처를 수정했다면 함께 add)
git commit -m "test(mission): 미션 목록 조회 E2E 추가(eligible·비활성 제외)"
```

---

## Task 9: 미션 보상 수령(claim) E2E

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/mission/ClaimMissionE2ETest.kt`

**Interfaces:**
- Consumes: `POST /missions/v1/{missionId}/claim`, `MissionEntityFixture`·`UserDetailEntityFixture`·`UserEntityFixture`·`CoinBalanceEntityFixture`, Q-엔티티(`QMissionEntity`·`QMissionCompletionEntity`·`QCoinBalanceEntity`·`QCoinHistoryEntity`·`QUserDetailEntity`).

- [ ] **Step 1: E2E 작성**

Create `ClaimMissionE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.mission

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MissionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.mission.command.entity.MissionEntity
import com.org.oneulsogae.infra.mission.command.entity.QMissionCompletionEntity
import com.org.oneulsogae.infra.mission.command.entity.QMissionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /missions/v1/{missionId}/claim` E2E 테스트.
 * 자격 충족 시 코인 적립 + 완료 기록, 재수령 409, 부적격 400, 없는 미션 404를 검증한다.
 */
class ClaimMissionE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QMissionCompletionEntity.missionCompletionEntity)
		IntegrationUtil.deleteAll(QMissionEntity.missionEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}

	fun completionCount(userId: Long, missionId: Long): Int {
		val c = QMissionCompletionEntity.missionCompletionEntity
		return IntegrationUtil.getQuery().selectFrom(c)
			.where(c.userId.eq(userId), c.missionId.eq(missionId)).fetch().size
	}

	fun balanceOf(userId: Long): Int? {
		val b = QCoinBalanceEntity.coinBalanceEntity
		return IntegrationUtil.getQuery().selectFrom(b).where(b.userId.eq(userId)).fetchOne()?.balance
	}

	fun missionRewardHistoryCount(userId: Long): Int {
		val h = QCoinHistoryEntity.coinHistoryEntity
		return IntegrationUtil.getQuery().selectFrom(h)
			.where(h.userId.eq(userId), h.coinGetType.eq(CoinGetType.MISSION)).fetch().size
	}

	describe("POST /missions/v1/{missionId}/claim") {

		context("자격을 충족한 사용자가 수령하면") {
			it("50코인을 적립하고 완료 기록을 남긴다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				val missionId: Long = IntegrationUtil.persist(MissionEntityFixture.create()).id!!

				post("/missions/v1/$missionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.rewardedCoin", 50)
					body("data.balance", 50)
				}

				completionCount(userId, missionId) shouldBe 1
				balanceOf(userId) shouldBe 50
				missionRewardHistoryCount(userId) shouldBe 1
			}
		}

		context("이미 수령한 미션을 다시 수령하면") {
			it("409(MISSION-003)이고 재적립되지 않는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				val missionId: Long = IntegrationUtil.persist(MissionEntityFixture.create()).id!!

				post("/missions/v1/$missionId/claim") { bearer(accessTokenFor(userId)) } expect { status(200) }

				post("/missions/v1/$missionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(409)
					body("error.code", "MISSION-003")
				}

				completionCount(userId, missionId) shouldBe 1
				balanceOf(userId) shouldBe 50
			}
		}

		context("자격 미충족(소개 100자 미만)으로 수령하면") {
			it("400(MISSION-002)이고 적립되지 않는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-3")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(50)))
				val missionId: Long = IntegrationUtil.persist(MissionEntityFixture.create()).id!!

				post("/missions/v1/$missionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(400)
					body("error.code", "MISSION-002")
				}

				completionCount(userId, missionId) shouldBe 0
				balanceOf(userId) shouldBe null
			}
		}

		context("없는(또는 비활성) 미션을 수령하면") {
			it("404(MISSION-001)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-4")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				val inactiveMissionId: Long = IntegrationUtil.persist(MissionEntityFixture.create(active = false)).id!!

				post("/missions/v1/$inactiveMissionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("error.code", "MISSION-001")
				}
			}
		}
	}
})
```

- [ ] **Step 2: 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.mission.ClaimMissionE2ETest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/mission/ClaimMissionE2ETest.kt
git commit -m "test(mission): 보상 수령 E2E 추가(적립·재수령409·부적격400·없음404)"
```

---

## Task 10: 전체 테스트 + 마이그레이션·시드 SQL

**Files:**
- Create: `docs/migration/missions.sql`·`mission_completions.sql`·`mission_seed_write_introduction.sql`

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. (기존 회귀 포함 전부 통과)

- [ ] **Step 2: 마이그레이션·시드 SQL 문서화**

`docs/migration/`의 기존 형식을 확인(`cat docs/migration/coin_item_purchases.sql`)하고 맞춘 뒤, 아래 3파일을 생성한다. 엔티티(Task 6)의 실제 컬럼 타입·제약과 1:1 대조한다.

Create `docs/migration/missions.sql`:

```sql
-- 미션 정의
CREATE TABLE missions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  type VARCHAR(50) NOT NULL,
  reward_coin INT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  display_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_missions_type (type)
);
```

Create `docs/migration/mission_completions.sql`:

```sql
-- 미션 완료 기록(회원당 미션 1회 가드)
CREATE TABLE mission_completions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  mission_id BIGINT NOT NULL,
  rewarded_coin INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_mission_completions_user_mission (user_id, mission_id)
);
```

Create `docs/migration/mission_seed_write_introduction.sql`:

```sql
-- 첫 미션: 자기소개 100자 이상 작성 → 50코인
INSERT INTO missions (type, reward_coin, title, description, active, display_order, created_at, updated_at)
VALUES ('WRITE_INTRODUCTION', 50, '자기소개 작성', '자기소개를 100자 이상 작성하고 50코인을 받으세요', 1, 0, NOW(6), NOW(6));
```

- [ ] **Step 3: Commit**

```bash
git add docs/migration/missions.sql docs/migration/mission_completions.sql docs/migration/mission_seed_write_introduction.sql
git commit -m "docs(mission): 미션·완료기록 마이그레이션 DDL·첫 미션 시드 추가"
```

---

## Self-Review

**Spec coverage:**
- `missions`(type UNIQUE·reward·title·desc·active·order) → Task 6·10. ✓
- `mission_completions`(UNIQUE user+mission) → Task 6·10. ✓
- MissionType + CoinGetType.MISSION → Task 1. ✓
- 평가자(MissionEvaluator·WriteIntroduction·resolver) → Task 3. ✓
- 미션 정의 query 읽기 모델 + claim이 query in-port 로드 → Task 2·4·5. ✓
- claim 원자 적립+가드(유니크 위반 → 409) → Task 5·9. ✓
- 목록 채널(completed·eligible) → Task 4·8. ✓
- MissionErrorCode 3종 → Task 2. ✓
- API GET·POST claim → Task 7·8·9. ✓
- 시드 + 마이그레이션 → Task 10. ✓

**타입 정합:** `getMissions(userId): MissionViews`, `getById(missionId): Mission`, `claim(userId, missionId): ClaimMissionResult`, `SaveMissionCompletionPort.save(userId, missionId, rewardedCoin)`, `MissionEvaluators.resolve(type): MissionEvaluator`, `MissionEvaluator.{supports(type), isEligible(userId)}`, `Mission(id·type·rewardCoin·title·description·displayOrder)` 6-arg 투영 = 생성자 6개 일치, `MissionView`/`MissionResponse` 7필드 일치, `ClaimMissionResult(rewardedCoin·balance)` = 응답 매핑 일치.

**주의(플랜 갭 방지):**
- `UserDetailEntityFixture.create`의 `introduction: String? = null` 인자, `CoinHistoryEntity.coinGetType: CoinGetType?`(컬럼 coin_get_type) 모두 존재 확인 완료.
- Task 3 TDD: Step 1(구현)을 먼저 두면 Step 2 테스트가 바로 GREEN이라 RED 관찰이 생략된다. 엄격 TDD를 원하면 테스트 파일을 먼저 만들어 컴파일 실패(RED)를 확인한 뒤 구현한다. 최종 커밋 코드가 GREEN이면 충분.
- `AcquireCoinUseCase.acquire`는 `CoinBalance`를 반환하고 `.balance`로 잔액을 얻는다(별도 잔액 조회 in-port 불필요). Task 5에서 이 반환을 그대로 사용.
- E2E `body(path, value)`/`body(path, matcher)`·`get`/`post`/`bearer`/`status`는 기존 `RestAssuredDsl`에 존재. `data.size()`는 기존 테스트(GetPopups…)에서 사용하는 관용구.

## Execution Handoff

계획을 `docs/superpowers/plans/2026-07-26-mission-reward.md`에 저장했다.
