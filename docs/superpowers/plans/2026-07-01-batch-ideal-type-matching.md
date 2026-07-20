# 배치 매칭 이상형 우선순위 적용 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 일일 솔로 매칭 배치가 이상형(`user_ideal_types`)을 필터가 아닌 **우선순위**로 반영해, 유저 풀에서 종합 점수(이상형·거리·최근)로 정렬해 매칭한다.

**Architecture:** scheduler에 스코어링 read model(`MatchScoringProfile`)과 순수 점수 계산기(`MatchScorer`)를 추가한다. infra 읽기 어댑터가 `user_details`+`user_ideal_types`를 조인해 프로필을 배치 시작 시 1회 적재하고, `SoloMatchBatchService`가 대상마다 가용 반대성별 후보 전체를 종합 점수로 정렬해 재소개 이력 없는 최고점 후보를 소개한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4, Spring Data JPA + QueryDSL, Kotest 5.9.1(JUnit5 러너), Testcontainers(MySQL) E2E.

## Global Constraints

- **응답·주석·커밋은 한국어.** 커밋 형식 `<type>(<domain>): <설명>`, 도메인은 `match`(솔로 배치는 solomatch 영역).
- **scheduler 모듈은 core·infra에 의존하지 않는다.** scheduler 코드는 `oneulsogae-common`의 enum과 JDK만 쓴다. 나이 계산 등 core 유틸(`ageAt`)은 **infra 어댑터에서만** 사용한다.
- **타입 명시**: 변수·반환·람다 파라미터 타입을 생략하지 않는다(표현식 본문 포함).
- **현재 시각 직접 호출 금지**: `LocalDateTime.now()` 대신 주입된 `TimeGenerator.now()`. 도메인에는 `now`/`today`를 파라미터로 전달.
- **이상형은 필터가 아니라 우선순위.** 이상형이 안 맞아도 후보가 있으면 매칭한다. 성별(반대)만 하드 제약이고, 지역·이상형은 점수에만 반영.
- **가중치**: 이상형 부합 0.4 / 거리 근접 0.4 / 최근 로그인 0.2. **점수 버킷** 0.05(상위 동점군 내 무작위).
- **스코어링 유닛 테스트는 `oneulsogae-api/src/test`** 아래에 둔다(scheduler 도메인 유닛 테스트의 기존 관례; scheduler 모듈에는 test 의존성이 없다).
- **CQS**: 조회는 `Get…Dao`(읽기 전용), 명령은 별도. 신규 조회 포트는 `query/dao`에 둔다.

---

## File Structure

**신규 (scheduler)**
- `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dto/MatchScoringProfile.kt` — 스코어링용 read model(속성 + 이상형). 순수 데이터.
- `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchScorer.kt` — 순수 점수 계산기(이상형 부합·거리·최근·종합·정렬). 프레임워크 무의존.
- `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dao/GetMatchScoringProfileDao.kt` — 조회 포트.

**신규 (infra)**
- `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/solomatch/query/GetMatchScoringProfileDaoImpl.kt` — `user_details`+`user_ideal_types` 조인 투영.

**수정 (scheduler)**
- `oneulsogae-scheduler/.../solomatch/command/domain/MatchPool.kt` — `availableCandidates(gender)` 추가.
- `oneulsogae-scheduler/.../solomatch/command/application/SoloMatchBatchService.kt` — 스코어링 기반 후보 선택으로 교체.

**신규 테스트 (oneulsogae-api)**
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchScorerTest.kt`
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/SoloMatchBatchServiceTest.kt`
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/GetMatchScoringProfileDaoIntegrationTest.kt`

**수정 테스트 (oneulsogae-api)**
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchPoolTest.kt` — `availableCandidates` 케이스 추가.
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunSoloMatchBatchIntegrationTest.kt` — 이상형 우선순위 시나리오 추가.

**변경하지 않음(보고만)**: `MatchPool.freshCandidates`/`regionsWith`, `RegionShuffler`/`RandomRegionShuffler`는 새 경로에서 미사용이 되지만 기존 테스트·타 배치(팀 매칭)가 참조하므로 삭제하지 않는다. 데드 여부는 최종 보고에만 남긴다.

---

## Task 1: 스코어링 read model + 이상형 부합 점수

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dto/MatchScoringProfile.kt`
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchScorer.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchScorerTest.kt`

**Interfaces:**
- Produces:
  - `data class MatchScoringProfile(userId: Long, age: Int?, height: Int?, maritalStatus: MaritalStatus?, smokingStatus: SmokingStatus?, drinkingStatus: DrinkingStatus?, religion: Religion?, idealAgeMin: Int?, idealAgeMax: Int?, idealHeightMin: Int?, idealHeightMax: Int?, idealMaritalStatus: MaritalStatus?, idealSmokingStatus: SmokingStatus?, idealDrinkingStatus: DrinkingStatus?, idealReligion: Religion?)`
  - `object MatchScorer { fun mutualIdealFit(target: MatchScoringProfile?, candidate: MatchScoringProfile?): Double }`

- [ ] **Step 1: 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchScorerTest.kt`:

```kotlin
package com.org.oneulsogae.scheduler.solomatch

import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.scheduler.solomatch.command.domain.MatchScorer
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchScoringProfile
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MatchScorer] 유닛 테스트. 이상형 부합(양방향)·거리·최근·종합 점수와 버킷 정렬을 프레임워크 없이 검증한다.
 */
class MatchScorerTest : DescribeSpec({

    // 지정한 조건만 채운 프로필 헬퍼. (나머지는 null = 미지정/상관없음)
    fun profile(
        userId: Long = 1L,
        age: Int? = null,
        height: Int? = null,
        maritalStatus: MaritalStatus? = null,
        smokingStatus: SmokingStatus? = null,
        drinkingStatus: DrinkingStatus? = null,
        religion: Religion? = null,
        idealAgeMin: Int? = null,
        idealAgeMax: Int? = null,
        idealHeightMin: Int? = null,
        idealHeightMax: Int? = null,
        idealMaritalStatus: MaritalStatus? = null,
        idealSmokingStatus: SmokingStatus? = null,
        idealDrinkingStatus: DrinkingStatus? = null,
        idealReligion: Religion? = null,
    ): MatchScoringProfile = MatchScoringProfile(
        userId, age, height, maritalStatus, smokingStatus, drinkingStatus, religion,
        idealAgeMin, idealAgeMax, idealHeightMin, idealHeightMax,
        idealMaritalStatus, idealSmokingStatus, idealDrinkingStatus, idealReligion,
    )

    describe("mutualIdealFit") {

        it("양쪽 다 이상형이 없으면 1.0(선호 없음)") {
            MatchScorer.mutualIdealFit(profile(), profile()) shouldBe 1.0
        }

        it("한 방향만 평가: 대상 이상형 1개 조건을 후보가 충족하고 후보는 이상형 없음 → (1.0+1.0)/2 = 1.0") {
            val target: MatchScoringProfile = profile(idealMaritalStatus = MaritalStatus.SINGLE)
            val candidate: MatchScoringProfile = profile(maritalStatus = MaritalStatus.SINGLE)
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 1.0
        }

        it("대상 이상형 1개 조건을 후보가 불충족, 후보는 이상형 없음 → (0.0+1.0)/2 = 0.5") {
            val target: MatchScoringProfile = profile(idealMaritalStatus = MaritalStatus.SINGLE)
            val candidate: MatchScoringProfile = profile(maritalStatus = MaritalStatus.DIVORCED)
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 0.5
        }

        it("지정 조건 2개 중 1개만 충족 → 방향 점수 0.5") {
            val target: MatchScoringProfile = profile(
                idealMaritalStatus = MaritalStatus.SINGLE,
                idealReligion = Religion.NONE,
            )
            val candidate: MatchScoringProfile = profile(
                maritalStatus = MaritalStatus.SINGLE,   // 충족
                religion = Religion.BUDDHISM,            // 불충족
            )
            // 대상→후보 = 1/2 = 0.5, 후보→대상 = 이상형 없음 = 1.0 → (0.5+1.0)/2 = 0.75
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 0.75
        }

        it("나이 범위: 경계 포함이면 충족") {
            val target: MatchScoringProfile = profile(idealAgeMin = 27, idealAgeMax = 35)
            MatchScorer.mutualIdealFit(target, profile(age = 27)) shouldBe 1.0
            MatchScorer.mutualIdealFit(target, profile(age = 35)) shouldBe 1.0
            MatchScorer.mutualIdealFit(target, profile(age = 26)) shouldBe 0.5   // (0+1)/2
        }

        it("후보 속성이 결측(null)이면 그 조건은 미충족") {
            val target: MatchScoringProfile = profile(idealHeightMin = 160, idealHeightMax = 175)
            MatchScorer.mutualIdealFit(target, profile(height = null)) shouldBe 0.5   // (0+1)/2
        }

        it("양방향 모두 충족하면 1.0") {
            val target: MatchScoringProfile = profile(
                age = 30, idealMaritalStatus = MaritalStatus.SINGLE,
            )
            val candidate: MatchScoringProfile = profile(
                maritalStatus = MaritalStatus.SINGLE, idealAgeMin = 28, idealAgeMax = 32,
            )
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 1.0
        }
    }
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.MatchScorerTest"`
Expected: 컴파일 실패(`MatchScoringProfile`·`MatchScorer` 미존재).

- [ ] **Step 3: read model 생성**

`oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dto/MatchScoringProfile.kt`:

```kotlin
package com.org.oneulsogae.scheduler.solomatch.query.dto

import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus

/**
 * 이상형 우선순위 스코어링에 쓰는 read model. 유저의 실제 속성과 이상형(선호)을 함께 담는다.
 * user_details + user_ideal_types를 조인해 배치 시작 시 1회 적재한다. (양방향 부합 계산에 양쪽 데이터가 필요)
 * 나이는 조회 시점 today 기준으로 계산해 담는다(무연산 read model). null 속성/이상형은 "미상/상관없음".
 */
data class MatchScoringProfile(
	val userId: Long,
	val age: Int?,
	val height: Int?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val drinkingStatus: DrinkingStatus?,
	val religion: Religion?,
	val idealAgeMin: Int?,
	val idealAgeMax: Int?,
	val idealHeightMin: Int?,
	val idealHeightMax: Int?,
	val idealMaritalStatus: MaritalStatus?,
	val idealSmokingStatus: SmokingStatus?,
	val idealDrinkingStatus: DrinkingStatus?,
	val idealReligion: Religion?,
)
```

- [ ] **Step 4: MatchScorer 이상형 부합 구현**

`oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchScorer.kt`:

```kotlin
package com.org.oneulsogae.scheduler.solomatch.command.domain

import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchScoringProfile

/**
 * 일일 매칭의 이상형 우선순위 점수 계산기(순수). 이상형은 필터가 아니라 우선순위이므로,
 * 부합하지 않아도 점수만 낮아진다. 이상형 부합(양방향)·거리·최근을 0~1로 정규화해 가중 합산한다.
 */
object MatchScorer {

	/**
	 * 두 프로필의 이상형 부합도(0~1, 양방향 평균).
	 * 각 방향은 "지정한 이상형 조건 중 상대 속성이 충족하는 비율"이며, 지정 조건이 없으면 1.0(선호 없음).
	 */
	fun mutualIdealFit(target: MatchScoringProfile?, candidate: MatchScoringProfile?): Double =
		(directionFit(target, candidate) + directionFit(candidate, target)) / 2.0

	/** [preference]의 이상형으로 [other]의 속성을 평가한 한 방향 부합도(0~1). 지정 조건이 없으면 1.0. */
	private fun directionFit(preference: MatchScoringProfile?, other: MatchScoringProfile?): Double {
		if (preference == null) return 1.0
		val results: List<Boolean> = buildList {
			if (preference.idealAgeMin != null && preference.idealAgeMax != null) {
				val age: Int? = other?.age
				add(age != null && age in preference.idealAgeMin..preference.idealAgeMax)
			}
			if (preference.idealHeightMin != null && preference.idealHeightMax != null) {
				val height: Int? = other?.height
				add(height != null && height in preference.idealHeightMin..preference.idealHeightMax)
			}
			if (preference.idealMaritalStatus != null) {
				add(other?.maritalStatus == preference.idealMaritalStatus)
			}
			if (preference.idealSmokingStatus != null) {
				add(other?.smokingStatus == preference.idealSmokingStatus)
			}
			if (preference.idealDrinkingStatus != null) {
				add(other?.drinkingStatus == preference.idealDrinkingStatus)
			}
			if (preference.idealReligion != null) {
				add(other?.religion == preference.idealReligion)
			}
		}
		if (results.isEmpty()) return 1.0
		return results.count { satisfied: Boolean -> satisfied }.toDouble() / results.size
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.MatchScorerTest"`
Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dto/MatchScoringProfile.kt \
        oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchScorer.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchScorerTest.kt
git commit -m "feat(match): 이상형 우선순위 스코어링 프로필·부합 점수 추가"
```

---

## Task 2: 거리·최근·종합 점수 + 버킷 정렬

**Files:**
- Modify: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchScorer.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchScorerTest.kt` (append)

**Interfaces:**
- Consumes: `MatchScorer.mutualIdealFit` (Task 1), `MatchableUser`(기존 `scheduler.solomatch.query.dto`, 필드 `userId·gender·regionId·lastLoginAt`).
- Produces:
  - `fun distanceScore(rank: Int?, regionCount: Int): Double`
  - `fun recencyScore(lastLoginAt: LocalDateTime, loginAfter: LocalDateTime, now: LocalDateTime): Double`
  - `fun combinedScore(idealFit: Double, distanceScore: Double, recencyScore: Double): Double`
  - `fun orderByScore(scored: List<Pair<MatchableUser, Double>>, random: Random): List<MatchableUser>`

- [ ] **Step 1: 실패 테스트 추가**

`MatchScorerTest.kt`의 최상위 `DescribeSpec({ ... })` 블록 안, 기존 `describe("mutualIdealFit")` 아래에 추가. 파일 상단 import에 다음을 추가:

```kotlin
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser
import io.kotest.matchers.collections.shouldContainExactly
import java.time.LocalDateTime
import kotlin.random.Random
```

추가할 describe 블록:

```kotlin
    describe("distanceScore") {
        it("같은 지역(rank 0)이면 1.0, 가장 먼 지역이면 0.0") {
            MatchScorer.distanceScore(rank = 0, regionCount = 5) shouldBe 1.0
            MatchScorer.distanceScore(rank = 4, regionCount = 5) shouldBe 0.0
            MatchScorer.distanceScore(rank = 2, regionCount = 5) shouldBe 0.5
        }
        it("근접 목록에 없는 지역(rank null)은 0.0, 지역이 1개 이하면 1.0") {
            MatchScorer.distanceScore(rank = null, regionCount = 5) shouldBe 0.0
            MatchScorer.distanceScore(rank = 0, regionCount = 1) shouldBe 1.0
        }
    }

    describe("recencyScore") {
        val loginAfter: LocalDateTime = LocalDateTime.of(2026, 6, 17, 12, 0)   // now - 2주
        val now: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)
        it("now에 로그인=1.0, 창 시작=0.0, 중간=0.5") {
            MatchScorer.recencyScore(now, loginAfter, now) shouldBe 1.0
            MatchScorer.recencyScore(loginAfter, loginAfter, now) shouldBe 0.0
            MatchScorer.recencyScore(LocalDateTime.of(2026, 6, 24, 12, 0), loginAfter, now) shouldBe 0.5
        }
    }

    describe("combinedScore") {
        it("이상형 0.4 / 거리 0.4 / 최근 0.2 가중합") {
            MatchScorer.combinedScore(idealFit = 1.0, distanceScore = 0.5, recencyScore = 0.0) shouldBe 0.6
            MatchScorer.combinedScore(idealFit = 0.0, distanceScore = 0.0, recencyScore = 1.0) shouldBe 0.2
        }
    }

    describe("orderByScore") {
        fun user(userId: Long): MatchableUser =
            MatchableUser(userId, Gender.FEMALE, regionId = 1L, lastLoginAt = LocalDateTime.of(2026, 7, 1, 12, 0))

        it("점수 내림차순으로 정렬한다(버킷이 다르면 결정적)") {
            val scored: List<Pair<MatchableUser, Double>> = listOf(
                user(1L) to 0.30,
                user(2L) to 0.90,
                user(3L) to 0.60,
            )
            MatchScorer.orderByScore(scored, Random(0)).map { u: MatchableUser -> u.userId } shouldContainExactly listOf(2L, 3L, 1L)
        }

        it("같은 버킷(0.05 이내) 안은 무작위지만 높은 버킷이 항상 앞선다") {
            // 0.90, 0.91 → 같은 버킷(18). 0.50 → 버킷 10. 앞 두 명이 어떤 순서든 셋째는 3L.
            val scored: List<Pair<MatchableUser, Double>> = listOf(
                user(1L) to 0.90,
                user(2L) to 0.91,
                user(3L) to 0.50,
            )
            val ordered: List<Long> = MatchScorer.orderByScore(scored, Random(42)).map { u: MatchableUser -> u.userId }
            ordered.last() shouldBe 3L
            ordered.take(2).sorted() shouldContainExactly listOf(1L, 2L)
        }
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.MatchScorerTest"`
Expected: 컴파일 실패(`distanceScore`·`recencyScore`·`combinedScore`·`orderByScore` 미존재).

- [ ] **Step 3: MatchScorer에 함수 추가**

`MatchScorer.kt` 상단 import에 추가:

```kotlin
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser
import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random
```

`object MatchScorer { ... }` 안(기존 `mutualIdealFit` 아래, `directionFit` 위)에 추가:

```kotlin
	/** 근접 순위 [rank](같은 지역=0, 없으면 null)를 0~1 점수로. 같은 지역=1.0, 가장 먼 지역=0.0, 목록 밖=0.0. */
	fun distanceScore(rank: Int?, regionCount: Int): Double {
		if (rank == null) return 0.0
		if (regionCount <= 1) return 1.0
		return 1.0 - rank.toDouble() / (regionCount - 1)
	}

	/** 2주 창(loginAfter~now)에서 [lastLoginAt]의 최근성 0~1. now=1.0, 창 시작=0.0. */
	fun recencyScore(lastLoginAt: LocalDateTime, loginAfter: LocalDateTime, now: LocalDateTime): Double {
		val windowSeconds: Long = Duration.between(loginAfter, now).seconds
		if (windowSeconds <= 0) return 1.0
		val elapsedSeconds: Long = Duration.between(loginAfter, lastLoginAt).seconds
		return (elapsedSeconds.toDouble() / windowSeconds).coerceIn(0.0, 1.0)
	}

	/** 세 요소를 가중 합산한 종합 점수(0~1). 이상형 0.4 / 거리 0.4 / 최근 0.2. */
	fun combinedScore(idealFit: Double, distanceScore: Double, recencyScore: Double): Double =
		IDEAL_WEIGHT * idealFit + DISTANCE_WEIGHT * distanceScore + RECENCY_WEIGHT * recencyScore

	/**
	 * 후보를 종합 점수 내림차순으로 정렬하되, [BUCKET_SIZE] 단위 버킷 안은 [random]으로 섞는다.
	 * (상위 동점군 내 무작위 — 매일 같은 상대만 뽑히지 않게 하면서 이상형 우선순위는 유지)
	 */
	fun orderByScore(scored: List<Pair<MatchableUser, Double>>, random: Random): List<MatchableUser> =
		scored
			.groupBy { (_, score: Double) -> (score / BUCKET_SIZE).toInt() }
			.entries
			.sortedByDescending { entry: Map.Entry<Int, List<Pair<MatchableUser, Double>>> -> entry.key }
			.flatMap { entry: Map.Entry<Int, List<Pair<MatchableUser, Double>>> ->
				entry.value.shuffled(random).map { (user: MatchableUser, _) -> user }
			}
```

그리고 `object MatchScorer {` 바로 아래(또는 파일 끝 `}` 직전)에 상수 블록 추가:

```kotlin
	private const val IDEAL_WEIGHT: Double = 0.4
	private const val DISTANCE_WEIGHT: Double = 0.4
	private const val RECENCY_WEIGHT: Double = 0.2

	/** 종합 점수 버킷 크기. 같은 버킷(≈동점)은 무작위로 섞는다. */
	private const val BUCKET_SIZE: Double = 0.05
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.MatchScorerTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchScorer.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchScorerTest.kt
git commit -m "feat(match): 거리·최근·종합 점수와 버킷 정렬 추가"
```

---

## Task 3: MatchPool 가용 반대성별 후보 조회

**Files:**
- Modify: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchPool.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchPoolTest.kt` (append)

**Interfaces:**
- Produces: `MatchPool.availableCandidates(gender: Gender): List<MatchableUser>` — 아직 가용한 해당 성별 후보 전체(지역 무관).

- [ ] **Step 1: 실패 테스트 추가**

`MatchPoolTest.kt` 하단, 최상위 spec 블록 안에 describe 추가. 기존 파일의 import·헬퍼(예: `MatchableUser` 생성)를 재사용한다. 재사용할 헬퍼가 없으면 아래 인라인 생성으로 작성:

```kotlin
    describe("availableCandidates") {
        val now: java.time.LocalDateTime = java.time.LocalDateTime.of(2026, 7, 1, 12, 0)
        fun u(userId: Long, gender: com.org.oneulsogae.common.user.Gender, regionId: Long): com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser =
            com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser(userId, gender, regionId, now)

        it("지역과 무관하게 해당 성별의 가용 후보를 모두 돌려준다") {
            val pool: com.org.oneulsogae.scheduler.solomatch.command.domain.MatchPool =
                com.org.oneulsogae.scheduler.solomatch.command.domain.MatchPool.of(
                    listOf(
                        u(1L, com.org.oneulsogae.common.user.Gender.FEMALE, 10L),
                        u(2L, com.org.oneulsogae.common.user.Gender.FEMALE, 20L),
                        u(3L, com.org.oneulsogae.common.user.Gender.MALE, 10L),
                    ),
                )

            pool.availableCandidates(com.org.oneulsogae.common.user.Gender.FEMALE)
                .map { it.userId }.sorted() shouldBe listOf(1L, 2L)
        }

        it("remove된 후보는 제외된다") {
            val female1: com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser = u(1L, com.org.oneulsogae.common.user.Gender.FEMALE, 10L)
            val pool: com.org.oneulsogae.scheduler.solomatch.command.domain.MatchPool =
                com.org.oneulsogae.scheduler.solomatch.command.domain.MatchPool.of(
                    listOf(female1, u(2L, com.org.oneulsogae.common.user.Gender.FEMALE, 20L)),
                )

            pool.remove(female1)

            pool.availableCandidates(com.org.oneulsogae.common.user.Gender.FEMALE).map { it.userId } shouldBe listOf(2L)
        }
    }
```

> 참고: 기존 `MatchPoolTest.kt`가 이미 `import com.org.oneulsogae.common.user.Gender` 등을 갖고 있으면 위 FQN을 그 import에 맞춰 축약해도 된다. FQN 그대로도 컴파일된다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.MatchPoolTest"`
Expected: 컴파일 실패(`availableCandidates` 미존재).

- [ ] **Step 3: MatchPool에 메서드 추가**

`MatchPool.kt`의 `regionsWith` 아래에 추가:

```kotlin
	/** [gender]의 아직 가용한 후보 전체(지역 무관). 이상형 종합 점수 정렬 경로에서 후보 집합으로 쓴다. */
	fun availableCandidates(gender: Gender): List<MatchableUser> =
		bucketsByKey.entries
			.filter { (key: BucketKey, _) -> key.gender == gender }
			.flatMap { (_, users: List<MatchableUser>) -> users }
			.filter { user: MatchableUser -> user.userId in available }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.MatchPoolTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/domain/MatchPool.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/MatchPoolTest.kt
git commit -m "feat(match): MatchPool 가용 반대성별 후보 조회 추가"
```

---

## Task 4: 스코어링 프로필 조회 포트 + 조인 어댑터

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dao/GetMatchScoringProfileDao.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/solomatch/query/GetMatchScoringProfileDaoImpl.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/GetMatchScoringProfileDaoIntegrationTest.kt`

**Interfaces:**
- Consumes: `MatchScoringProfile`(Task 1), core `com.org.oneulsogae.core.common.time.ageAt(today: LocalDate): Int`(infra에서만 사용), QueryDSL `QUserDetailEntity`·`QUserIdealTypeEntity`.
- Produces: `interface GetMatchScoringProfileDao { fun load(userIds: Set<Long>, today: LocalDate): Map<Long, MatchScoringProfile> }`

- [ ] **Step 1: 실패 통합 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/GetMatchScoringProfileDaoIntegrationTest.kt`:

```kotlin
package com.org.oneulsogae.api.scheduler

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserIdealTypeEntity
import com.org.oneulsogae.infra.user.command.entity.UserIdealTypeEntity
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchScoringProfile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * [GetMatchScoringProfileDao] 통합 테스트. user_details + user_ideal_types 조인 투영과 나이 계산을 검증한다.
 */
class GetMatchScoringProfileDaoIntegrationTest(
	private val getMatchScoringProfileDao: GetMatchScoringProfileDao,
) : AbstractIntegrationSupport({

	describe("load") {

		it("user_details의 속성과 나이(today 기준), 이상형을 함께 투영한다") {
			IntegrationUtil.persist(
				UserDetailEntityFixture.create(userId = 1L, gender = Gender.FEMALE, birthday = LocalDate.of(1996, 1, 1)),
			)
			IntegrationUtil.persist(
				UserIdealTypeEntity(userId = 1L, ageMin = 28, ageMax = 34, maritalStatus = MaritalStatus.SINGLE),
			)

			val profiles: Map<Long, MatchScoringProfile> = getMatchScoringProfileDao.load(setOf(1L), LocalDate.of(2026, 7, 1))

			val profile: MatchScoringProfile = profiles[1L].shouldNotBeNull()
			profile.age shouldBe 30
			profile.idealAgeMin shouldBe 28
			profile.idealAgeMax shouldBe 34
			profile.idealMaritalStatus shouldBe MaritalStatus.SINGLE
		}

		it("이상형이 없으면 이상형 필드는 null(속성만 채워진다)") {
			IntegrationUtil.persist(
				UserDetailEntityFixture.create(userId = 2L, gender = Gender.MALE, birthday = LocalDate.of(1990, 6, 1)),
			)

			val profiles: Map<Long, MatchScoringProfile> = getMatchScoringProfileDao.load(setOf(2L), LocalDate.of(2026, 7, 1))

			val profile: MatchScoringProfile = profiles[2L].shouldNotBeNull()
			profile.age shouldBe 36
			profile.idealAgeMin.shouldBeNull()
			profile.idealMaritalStatus.shouldBeNull()
		}

		it("빈 userIds면 빈 맵을 돌려준다") {
			getMatchScoringProfileDao.load(emptySet(), LocalDate.of(2026, 7, 1)) shouldBe emptyMap()
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserIdealTypeEntity.userIdealTypeEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.GetMatchScoringProfileDaoIntegrationTest"`
Expected: 컴파일 실패(`GetMatchScoringProfileDao` 미존재).

- [ ] **Step 3: 포트 생성**

`oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dao/GetMatchScoringProfileDao.kt`:

```kotlin
package com.org.oneulsogae.scheduler.solomatch.query.dao

import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchScoringProfile
import java.time.LocalDate

/**
 * 이상형 우선순위 스코어링 프로필 조회 포트. 배치 시작 시 대상 userId 집합에 대해 1회 적재한다.
 * 구현(infra)이 user_details + user_ideal_types를 조인해 [today] 기준 나이까지 계산한 read model로 투영한다.
 */
interface GetMatchScoringProfileDao {

	/** [userIds]의 스코어링 프로필을 userId→프로필 맵으로 돌려준다. (user_details가 없는 userId는 결과에 없음) */
	fun load(userIds: Set<Long>, today: LocalDate): Map<Long, MatchScoringProfile>
}
```

- [ ] **Step 4: 어댑터 구현**

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/solomatch/query/GetMatchScoringProfileDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.solomatch.query

import com.org.oneulsogae.core.common.time.ageAt
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserIdealTypeEntity
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchScoringProfile
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * [GetMatchScoringProfileDao]의 QueryDSL 구현. user_details를 기준으로 user_ideal_types를 left join해
 * 스코어링 프로필로 투영한다. (매칭 대상은 프로필을 갖춘 유저이므로 user_details 기준이 안전, 이상형은 선택적)
 * 나이는 birthday를 [today] 기준으로 계산해 담는다. soft delete 행은 각 엔티티의 @SQLRestriction으로 제외된다.
 * userId in (:userIds) 등치 IN은 user_details PK/유니크(user_id)로 받쳐진다.
 */
@Component
class GetMatchScoringProfileDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchScoringProfileDao {

	override fun load(userIds: Set<Long>, today: LocalDate): Map<Long, MatchScoringProfile> {
		if (userIds.isEmpty()) return emptyMap()
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val ideal: QUserIdealTypeEntity = QUserIdealTypeEntity.userIdealTypeEntity
		val tuples: List<Tuple> = queryFactory
			.select(
				detail.userId,
				detail.birthday,
				detail.height,
				detail.maritalStatus,
				detail.smokingStatus,
				detail.drinkingStatus,
				detail.religion,
				ideal.ageMin,
				ideal.ageMax,
				ideal.heightMin,
				ideal.heightMax,
				ideal.maritalStatus,
				ideal.smokingStatus,
				ideal.drinkingStatus,
				ideal.religion,
			)
			.from(detail)
			.leftJoin(ideal).on(ideal.userId.eq(detail.userId))
			.where(detail.userId.`in`(userIds))
			.fetch()
		return tuples.associate { tuple: Tuple ->
			val userId: Long = tuple.get(detail.userId)!!
			userId to MatchScoringProfile(
				userId = userId,
				age = tuple.get(detail.birthday)?.ageAt(today),
				height = tuple.get(detail.height),
				maritalStatus = tuple.get(detail.maritalStatus),
				smokingStatus = tuple.get(detail.smokingStatus),
				drinkingStatus = tuple.get(detail.drinkingStatus),
				religion = tuple.get(detail.religion),
				idealAgeMin = tuple.get(ideal.ageMin),
				idealAgeMax = tuple.get(ideal.ageMax),
				idealHeightMin = tuple.get(ideal.heightMin),
				idealHeightMax = tuple.get(ideal.heightMax),
				idealMaritalStatus = tuple.get(ideal.maritalStatus),
				idealSmokingStatus = tuple.get(ideal.smokingStatus),
				idealDrinkingStatus = tuple.get(ideal.drinkingStatus),
				idealReligion = tuple.get(ideal.religion),
			)
		}
	}
}
```

> 참고: `detail.maritalStatus`와 `ideal.maritalStatus`(및 smoking/drinking/religion)는 같은 enum 타입이라 `Tuple.get`이 순서(position)로 구분한다. 위 select 순서를 그대로 유지할 것.

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.GetMatchScoringProfileDaoIntegrationTest"`
Expected: PASS.

> 실패 시 점검: `ageAt` import 경로(`com.org.oneulsogae.core.common.time.ageAt`), `UserIdealTypeEntity` 생성자 파라미터명(`ageMin`/`ageMax`/`maritalStatus`), `Tuple` import(`com.querydsl.core.Tuple`).

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/query/dao/GetMatchScoringProfileDao.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/solomatch/query/GetMatchScoringProfileDaoImpl.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/GetMatchScoringProfileDaoIntegrationTest.kt
git commit -m "feat(match): 이상형 스코어링 프로필 조회 포트·조인 어댑터 추가"
```

---

## Task 5: 배치 서비스에 이상형 종합 점수 적용

**Files:**
- Modify: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/application/SoloMatchBatchService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/SoloMatchBatchServiceTest.kt`

**Interfaces:**
- Consumes: `GetMatchScoringProfileDao.load`(Task 4), `MatchScorer.*`(Task 1·2), `MatchPool.availableCandidates`(Task 3), 기존 `RegionProximityPort.nearbyRegionIds`, `GetMatchRecordDao.existsByPair`, `SaveMatchRecordPort.saveProposedMatch`.
- Produces: 동작이 바뀐 `SoloMatchBatchService`(생성자에 `getMatchScoringProfileDao: GetMatchScoringProfileDao`, `random: Random = Random.Default` 추가, `regionShuffler: RegionShuffler` 제거).

- [ ] **Step 1: 실패 유닛 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/SoloMatchBatchServiceTest.kt`:

```kotlin
package com.org.oneulsogae.scheduler.solomatch

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.scheduler.common.command.application.port.out.NoIntroductionAlarmPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.RegionProximityPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.solomatch.command.application.SoloMatchBatchService
import com.org.oneulsogae.scheduler.solomatch.command.application.port.out.SaveMatchRecordPort
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchRecordDao
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchableUserDao
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchScoringProfile
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchedUserIds
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [SoloMatchBatchService] 유닛 테스트. 이상형은 필터가 아니라 우선순위임을 중심으로 검증한다.
 * 모든 협력자는 페이크로 대체하고, 시각·랜덤을 고정해 결정적으로 만든다.
 */
class SoloMatchBatchServiceTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)

	// saves: (requesterId, partnerId) 소개 기록. existsPairs: 이미 재소개 이력 있는 쌍(정렬된 Pair).
	fun service(
		matchables: List<MatchableUser>,
		profiles: Map<Long, MatchScoringProfile>,
		existsPairs: Set<Pair<Long, Long>>,
		saves: MutableList<Pair<Long, Long>>,
	): SoloMatchBatchService =
		SoloMatchBatchService(
			getMatchableUserDao = object : GetMatchableUserDao {
				override fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser> = matchables
			},
			getMatchScoringProfileDao = object : GetMatchScoringProfileDao {
				override fun load(userIds: Set<Long>, today: LocalDate): Map<Long, MatchScoringProfile> =
					profiles.filterKeys { it in userIds }
			},
			getMatchRecordDao = object : GetMatchRecordDao {
				override fun existsByPair(userIdA: Long, userIdB: Long): Boolean =
					(minOf(userIdA, userIdB) to maxOf(userIdA, userIdB)) in existsPairs
				override fun findMatchedUserIds(): MatchedUserIds = MatchedUserIds(emptySet())
				override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> = emptySet()
			},
			saveMatchRecordPort = object : SaveMatchRecordPort {
				override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
					saves.add(requesterId to partnerId)
				}
			},
			regionProximityPort = object : RegionProximityPort {
				override fun refresh() {}
				override fun nearbyRegionIds(regionId: Long): List<Long> = listOf(regionId)
			},
			timeGenerator = object : TimeGenerator {
				override fun now(): LocalDateTime = now
			},
			noIntroductionAlarmPort = object : NoIntroductionAlarmPort {
				override fun notifySoloUnmatched(userIds: Collection<Long>, now: LocalDateTime) {}
				override fun notifyTeamUnmatched(teamIds: Collection<Long>, now: LocalDateTime) {}
			},
			random = Random(0),
		)

	// 지정한 이상형/속성만 채운 프로필.
	fun profile(userId: Long, maritalStatus: MaritalStatus? = null, idealMaritalStatus: MaritalStatus? = null): MatchScoringProfile =
		MatchScoringProfile(
			userId = userId, age = null, height = null, maritalStatus = maritalStatus,
			smokingStatus = null, drinkingStatus = null, religion = null,
			idealAgeMin = null, idealAgeMax = null, idealHeightMin = null, idealHeightMax = null,
			idealMaritalStatus = idealMaritalStatus, idealSmokingStatus = null, idealDrinkingStatus = null, idealReligion = null,
		)

	fun male(userId: Long, lastLoginAt: LocalDateTime): MatchableUser = MatchableUser(userId, Gender.MALE, 1L, lastLoginAt)
	fun female(userId: Long, lastLoginAt: LocalDateTime): MatchableUser = MatchableUser(userId, Gender.FEMALE, 1L, lastLoginAt)

	describe("run - 이상형 우선순위") {

		it("거리·최근이 같으면 이상형이 더 맞는 후보를 우선 소개한다") {
			// 대상 1001(남, 가장 최근)의 이상형: 미혼. 1002는 미혼(부합), 1003은 돌싱(불충족).
			val matchables: List<MatchableUser> = listOf(
				male(1001L, now),
				female(1002L, now.minusMinutes(1)),
				female(1003L, now.minusMinutes(1)),
			)
			val profiles: Map<Long, MatchScoringProfile> = mapOf(
				1001L to profile(1001L, idealMaritalStatus = MaritalStatus.SINGLE),
				1002L to profile(1002L, maritalStatus = MaritalStatus.SINGLE),
				1003L to profile(1003L, maritalStatus = MaritalStatus.DIVORCED),
			)
			val saves: MutableList<Pair<Long, Long>> = mutableListOf()

			val result = service(matchables, profiles, existsPairs = emptySet(), saves).run()

			result.recommended shouldBe 1
			saves shouldContainExactlyInAnyOrder listOf(1001L to 1002L)
		}

		it("이상형이 전혀 안 맞아도 다른 후보가 없으면 소개한다(필터 아님)") {
			// 대상 1001 이상형: 미혼. 유일 후보 1003은 돌싱(불충족)이지만 그래도 소개돼야 한다.
			val matchables: List<MatchableUser> = listOf(male(1001L, now), female(1003L, now.minusMinutes(1)))
			val profiles: Map<Long, MatchScoringProfile> = mapOf(
				1001L to profile(1001L, idealMaritalStatus = MaritalStatus.SINGLE),
				1003L to profile(1003L, maritalStatus = MaritalStatus.DIVORCED),
			)
			val saves: MutableList<Pair<Long, Long>> = mutableListOf()

			val result = service(matchables, profiles, existsPairs = emptySet(), saves).run()

			result.recommended shouldBe 1
			saves shouldContainExactlyInAnyOrder listOf(1001L to 1003L)
		}

		it("재소개 이력이 있는 최고점 후보는 건너뛰고 다음 후보를 소개한다") {
			// 1001-1002는 이력 있음. 이상형상 1002가 최고점이지만 건너뛰고 1003과 소개.
			val matchables: List<MatchableUser> = listOf(
				male(1001L, now),
				female(1002L, now.minusMinutes(1)),
				female(1003L, now.minusMinutes(2)),
			)
			val profiles: Map<Long, MatchScoringProfile> = mapOf(
				1001L to profile(1001L, idealMaritalStatus = MaritalStatus.SINGLE),
				1002L to profile(1002L, maritalStatus = MaritalStatus.SINGLE),
				1003L to profile(1003L, maritalStatus = MaritalStatus.DIVORCED),
			)
			val saves: MutableList<Pair<Long, Long>> = mutableListOf()

			val result = service(matchables, profiles, existsPairs = setOf(1001L to 1002L), saves).run()

			result.recommended shouldBe 1
			saves shouldContainExactlyInAnyOrder listOf(1001L to 1003L)
		}
	}
})
```

> 참고: `MatchedUserIds`의 실제 위치·생성자를 Task 시작 시 확인한다(`grep -rn "class MatchedUserIds" oneulsogae-scheduler/src/main`). 위 import·생성 방식은 그 정의에 맞춘다. `GetMatchRecordDao`의 메서드 시그니처도 실제 인터페이스와 일치시킨다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.SoloMatchBatchServiceTest"`
Expected: 컴파일 실패(생성자에 `getMatchScoringProfileDao`·`random` 없음, `regionShuffler` 여전히 필요).

- [ ] **Step 3: 서비스 교체**

`SoloMatchBatchService.kt`를 아래로 교체(생성자·import·`findNearestFreshPartner`가 바뀐다). `RegionShuffler` import·의존 제거, `MatchScorer`·`GetMatchScoringProfileDao`·`MatchScoringProfile`·`Random` import 추가:

```kotlin
package com.org.oneulsogae.scheduler.solomatch.command.application

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.solomatch.command.application.port.`in`.RunSoloMatchBatchUseCase
import com.org.oneulsogae.scheduler.common.command.application.port.out.NoIntroductionAlarmPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.RegionProximityPort
import com.org.oneulsogae.scheduler.solomatch.command.application.port.out.SaveMatchRecordPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.solomatch.command.domain.MatchPool
import com.org.oneulsogae.scheduler.solomatch.command.domain.MatchScorer
import com.org.oneulsogae.scheduler.solomatch.command.domain.SoloMatchBatchResult
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchRecordDao
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.org.oneulsogae.scheduler.solomatch.query.dao.GetMatchableUserDao
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchScoringProfile
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [RunSoloMatchBatchUseCase] 구현. 매일 정오에 도는 일일 매칭 배치.
 *
 * "2주 내 활성 + 오늘 미매칭 + 성사 상태 아님" 유저를 한 번 적재해 [MatchPool]을 만들고,
 * 스코어링 프로필([GetMatchScoringProfileDao])을 1회 적재한다. 대상마다 가용 반대 성별 후보 전체를
 * 이상형·거리·최근 종합 점수([MatchScorer])로 정렬해, 재소개 이력([GetMatchRecordDao.existsByPair])이 없는
 * 최고점 후보와 PROPOSED 소개를 만든다. 이상형은 필터가 아니라 우선순위라 안 맞아도 후보가 있으면 소개한다.
 * 한 사용자의 실패가 다른 사용자에 전파되지 않도록 대상 단위로 격리하고, 예외만 failed로 집계한다.
 */
@Service
class SoloMatchBatchService(
	private val getMatchableUserDao: GetMatchableUserDao,
	private val getMatchScoringProfileDao: GetMatchScoringProfileDao,
	private val getMatchRecordDao: GetMatchRecordDao,
	private val saveMatchRecordPort: SaveMatchRecordPort,
	private val regionProximityPort: RegionProximityPort,
	private val timeGenerator: TimeGenerator,
	private val noIntroductionAlarmPort: NoIntroductionAlarmPort,
	private val random: Random = Random.Default,
) : RunSoloMatchBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): SoloMatchBatchResult {
		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
		val today: LocalDate = now.toLocalDate()

		// 근접·유저분포 스냅샷을 최신화한다. (온보딩 경로도 이득)
		regionProximityPort.refresh()

		// 신규 소개에서 제외할 유저: 이미 성사(MATCHED) 상태 + 오늘 한 번이라도 소개된 유저.
		val excluded: Set<Long> =
			getMatchRecordDao.findMatchedUserIds().values + getMatchRecordDao.findUserIdsIntroducedOn(today)

		val matchables: List<MatchableUser> = getMatchableUserDao.findMatchableUsers(loginAfter)
			.filterNot { user: MatchableUser -> user.userId in excluded }
		val pool: MatchPool = MatchPool.of(matchables)
		// 이상형 우선순위 스코어링 프로필을 대상 전체에 대해 1회 적재한다. (user_details 없는 유저는 맵에 없음 → 선호 없음 취급)
		val profiles: Map<Long, MatchScoringProfile> =
			getMatchScoringProfileDao.load(matchables.mapTo(mutableSetOf()) { user: MatchableUser -> user.userId }, today)

		var recommended = 0
		var skipped = 0
		var failed = 0
		for (target: MatchableUser in matchables) {
			if (!pool.contains(target)) continue // 이번 실행에서 이미 짝지어진 유저
			try {
				val partner: MatchableUser? = findBestFreshPartner(target, pool, profiles, now, loginAfter)
				if (partner == null) {
					skipped++
					continue
				}
				saveMatchRecordPort.saveProposedMatch(target.userId, target.gender, partner.userId, now)
				pool.remove(target)
				pool.remove(partner)
				recommended++
			} catch (e: Exception) {
				failed++
				log.warn("일일 매칭 처리 실패 userId={}", target.userId, e)
			}
		}

		// 루프가 끝난 뒤 풀에 남은(=끝까지 소개받지 못한) 유저에게만 "오늘 소개 없음" 알람을 보낸다.
		noIntroductionAlarmPort.notifySoloUnmatched(pool.remainingUserIds(), now)

		val result: SoloMatchBatchResult = SoloMatchBatchResult(targets = matchables.size, recommended = recommended, skipped = skipped, failed = failed)
		log.info("일일 매칭 배치 완료: {}", result)
		return result
	}

	/**
	 * [target]의 가용 반대 성별 후보 전체를 이상형·거리·최근 종합 점수로 정렬해,
	 * 재소개 이력이 없는 최고점 후보를 돌려준다. (없으면 null) 점수 동점군은 무작위로 섞는다.
	 */
	private fun findBestFreshPartner(
		target: MatchableUser,
		pool: MatchPool,
		profiles: Map<Long, MatchScoringProfile>,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
	): MatchableUser? {
		val partnerGender: Gender = target.gender.opposite()
		val targetProfile: MatchScoringProfile? = profiles[target.userId]
		// 대상 지역 기준 근접 순위(같은 지역=0). 좌표 없는 지역이면 빈 리스트라 거리 점수는 전원 0이 된다.
		val nearby: List<Long> = regionProximityPort.nearbyRegionIds(target.regionId)
		val rankByRegion: Map<Long, Int> = nearby.withIndex().associate { (index: Int, regionId: Long) -> regionId to index }

		val scored: List<Pair<MatchableUser, Double>> = pool.availableCandidates(partnerGender)
			.map { candidate: MatchableUser ->
				val idealFit: Double = MatchScorer.mutualIdealFit(targetProfile, profiles[candidate.userId])
				val distanceScore: Double = MatchScorer.distanceScore(rankByRegion[candidate.regionId], nearby.size)
				val recencyScore: Double = MatchScorer.recencyScore(candidate.lastLoginAt, loginAfter, now)
				candidate to MatchScorer.combinedScore(idealFit, distanceScore, recencyScore)
			}

		return MatchScorer.orderByScore(scored, random)
			.firstOrNull { candidate: MatchableUser -> !getMatchRecordDao.existsByPair(target.userId, candidate.userId) }
	}

	companion object {
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
```

- [ ] **Step 4: 유닛 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.solomatch.SoloMatchBatchServiceTest"`
Expected: PASS.

- [ ] **Step 5: 기존 배치 통합 테스트 회귀 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.RunSoloMatchBatchIntegrationTest"`
Expected: PASS. (기존 시나리오는 user_details 미적재라 프로필 맵이 비어 이상형 점수가 균일 → 거리·최근만으로 기존과 동일 동작)

> 실패 시: `regionShuffler`를 주입하던 다른 설정/테스트가 있는지 확인. `SoloMatchBatchService` 생성자에서 `regionShuffler`를 제거했으므로, 이를 명시 생성하던 코드가 있으면 함께 정리한다(없을 것으로 예상 — Spring 자동 주입).

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/solomatch/command/application/SoloMatchBatchService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/solomatch/SoloMatchBatchServiceTest.kt
git commit -m "feat(match): 일일 배치에 이상형 종합 점수 우선순위 적용"
```

---

## Task 6: 이상형 우선순위 E2E 시나리오

**Files:**
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunSoloMatchBatchIntegrationTest.kt`

**Interfaces:**
- Consumes: 실 컨텍스트의 `SoloMatchBatchService`(Task 5), `UserDetailEntity`·`UserIdealTypeEntity`, `IntegrationUtil`.

- [ ] **Step 1: 실패 시나리오 + 정리/헬퍼 추가**

`RunSoloMatchBatchIntegrationTest.kt` 상단 import에 추가:

```kotlin
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserIdealTypeEntity
import com.org.oneulsogae.infra.user.command.entity.UserIdealTypeEntity
import java.time.LocalDate
```

기존 `describe("run") { ... }` 안, 마지막 context 뒤에 시나리오 추가:

```kotlin
		context("이상형이 더 잘 맞는 후보가 있으면(거리·최근 동일)") {
			it("이상형 부합도가 높은 후보를 우선 소개한다(필터가 아니라 우선순위)") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val now: LocalDateTime = LocalDateTime.now()
				// 대상 남성(가장 최근 로그인)의 이상형: 미혼. 두 여성은 같은 지역·같은 로그인 시각 → 거리·최근 동일.
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId, lastLoginAt = now)
				val singleFemaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = now.minusMinutes(1))
				val divorcedFemaleId: Long = persistMatchableUser(userId = 1003L, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = now.minusMinutes(1))
				persistUserDetail(userId = 1001L, gender = Gender.MALE)
				persistUserDetail(userId = 1002L, gender = Gender.FEMALE, maritalStatus = MaritalStatus.SINGLE)
				persistUserDetail(userId = 1003L, gender = Gender.FEMALE, maritalStatus = MaritalStatus.DIVORCED)
				persistIdealType(userId = 1001L, maritalStatus = MaritalStatus.SINGLE)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 1
				proposedMatchBetween(maleId, singleFemaleId).shouldNotBeNull()    // 이상형 부합 → 우선
				proposedMatchBetween(maleId, divorcedFemaleId).shouldBeNull()
			}
		}
```

`afterTest` 블록에 user_details·user_ideal_types 정리를 추가(기존 deleteAll들 위에):

```kotlin
		IntegrationUtil.deleteAll(QUserIdealTypeEntity.userIdealTypeEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
```

파일 하단 private 헬퍼들 옆에 추가:

```kotlin
private fun persistUserDetail(userId: Long, gender: Gender, maritalStatus: MaritalStatus? = null) {
	IntegrationUtil.persist(
		UserDetailEntityFixture.create(userId = userId, gender = gender, birthday = LocalDate.of(1996, 1, 1))
			.apply { this.maritalStatus = maritalStatus },
	)
}

private fun persistIdealType(userId: Long, maritalStatus: MaritalStatus) {
	IntegrationUtil.persist(UserIdealTypeEntity(userId = userId, maritalStatus = maritalStatus))
}
```

> `UserDetailEntityFixture.create`에는 `maritalStatus` 파라미터가 없으므로 생성 후 `apply`로 세팅한다(엔티티의 `var maritalStatus`).

- [ ] **Step 2: 새 시나리오 실패/통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.RunSoloMatchBatchIntegrationTest"`
Expected: 새 시나리오 포함 전체 PASS. (헬퍼 미구현 상태로 먼저 돌리면 컴파일 실패 → 구현 후 PASS)

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunSoloMatchBatchIntegrationTest.kt
git commit -m "test(match): 배치 이상형 우선순위 E2E 시나리오 추가"
```

---

## 최종 검증

- [ ] **전체 빌드·테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 실패 시 해당 모듈 테스트 로그로 원인 확인.

- [ ] **데드코드 보고(삭제 아님)**

`MatchPool.freshCandidates`/`regionsWith`, `RegionShuffler`/`RandomRegionShuffler`가 솔로 배치 경로에서 미사용이 되었는지 확인하고, 팀 매칭 등 다른 참조가 남아있으면 그대로 둔다. 참조가 완전히 사라진 경우에만 사용자에게 삭제 여부를 확인한다.

---

## Self-Review 결과

**Spec coverage**: 데이터 조달(Task 4) · 종합 점수 공식 이상형/거리/최근(Task 1·2) · 양방향(Task 1 `mutualIdealFit`) · 알고리즘 통합(Task 5) · 상위 동점군 무작위(Task 2 `orderByScore`) · 필터 아님(Task 5·6 테스트) · 이상형 `distance` 제외(프로필에 미포함) · 테스트 전략(유닛+DAO 통합+E2E) 모두 태스크에 매핑됨.

**Placeholder scan**: 모든 코드/명령/기대값이 구체적으로 채워져 있음.

**Type consistency**: `MatchScoringProfile`(15필드) 생성자 순서가 Task 1 정의·Task 4 어댑터·Task 5 테스트에서 일치. `GetMatchScoringProfileDao.load(Set<Long>, LocalDate)` 시그니처가 포트·어댑터·서비스·테스트에서 일치. `MatchScorer` 함수명(`mutualIdealFit`·`distanceScore`·`recencyScore`·`combinedScore`·`orderByScore`)이 전 태스크에서 일치. 서비스 생성자 파라미터(신규 `getMatchScoringProfileDao`·`random`, 제거 `regionShuffler`)가 Task 5 구현·테스트에서 일치.

**확인 완료**: `GetMatchRecordDao`(`existsByPair`/`findMatchedUserIds():MatchedUserIds`/`findUserIdsIntroducedOn`), `MatchedUserIds(values: Set<Long>)`(`query.dto`), `NoIntroductionAlarmPort`(`notifySoloUnmatched`·`notifyTeamUnmatched`, 둘 다 `Collection<Long>`) 시그니처는 실제 코드와 대조해 Task 5 페이크에 반영함.

**주의(실행 시 확인)**: `UserIdealTypeEntity` 생성자 파라미터명(`ageMin`/`ageMax`/`maritalStatus` 등, Task 4·6)만 태스크 시작 시 대조 후 진행(엔티티 정의상 일치 예상).
