# 추가 소개(Extra Intro) 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 오늘의 추천 외에 한 명을 더 소개받는 "추가 소개" 기능(자격 후보 조회 API + 추가 소개 API)을 solomatch 도메인에 추가한다.

**Architecture:** 배치와 실시간 추가소개가 동일한 매칭 알고리즘을 쓰도록 `MatchScorer`/`MatchSelector`를 새 순수 모듈 `meeple-matching`으로 분리한다. 조회는 CQRS query, 추가소개는 command로 구현하고, 코인 차감·매칭 생성·기존 interest 흐름을 재사용한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Gradle 멀티모듈(헥사고날) / QueryDSL(OpenFeign 포크) / Kotest + RestAssured + Testcontainers.

## Global Constraints

- 응답·주석·커밋 메시지는 **한국어**. `meeple-backend`만 수정(프론트는 안내만).
- 커밋 메시지: `<type>(<domain>): <설명>`, 도메인은 `match`. 커밋 말미에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- 타입 명시(변수·반환·람다 파라미터). `LocalDateTime.now()` 직접 호출 금지 → `TimeGenerator` 주입. 현재 브랜치: `feat/extra-intro`.
- CQRS: command/query 포트·서비스·트랜잭션 분리. query는 자기 dao에만 의존. command out-port는 `command/adapter`, 조회는 `query`의 `*DaoImpl`.
- 도메인 간 참조는 in-port `UseCase` 주입(coin `SpendCoinUseCase`, matchuser `GetMatchUserUseCase`). 자기 도메인 영속성은 자기 out-port.
- 매칭 알고리즘 상수(가중치 0.4/0.4/0.2, 버킷 0.05, 최근 로그인 2주)는 기존 값 그대로 유지.
- 각 태스크 끝에서 해당 모듈 컴파일/테스트 통과 후 커밋.

---

## 파일 구조 개요

**신규 모듈 `meeple-matching`** (`com.org.meeple.matching`)
- `MatchScorer.kt` (scheduler에서 이동, `orderByScore` 제네릭화)
- `MatchScoringProfile.kt` (scheduler query dto에서 이동)
- `ScoringCandidate.kt` (신규 인터페이스)
- `MatchSelector.kt` (신규 선택 로직)

**meeple-common**
- `coin/CoinUsageType.kt` (EXTRA_INTRO 추가)
- `match/SoloMatchType.kt` (EXTRA 추가)

**meeple-scheduler** (적응)
- `query/dto/MatchableUser.kt` (`ScoringCandidate` 구현)
- `command/domain/MatchScorer.kt`, `query/dto/MatchScoringProfile.kt` (삭제 — matching으로 이동)
- `command/application/SoloMatchBatchService.kt` (`MatchSelector` 사용으로 교체)

**meeple-core / solomatch**
- `MatchErrorCode.kt` (EXTRA_INTRO_NO_CANDIDATE, MATCH_USER_NOT_MATCHABLE 추가)
- `command/application/port/in/IntroduceExtraMatchUseCase.kt`
- `command/application/IntroduceExtraMatchService.kt`
- `command/application/port/out/GetExtraIntroCandidatePort.kt` (+ `ExtraIntroCandidateRow` read model)
- `common/region/GetRegionProximityPort.kt` (신규 공용 out-port)
- `query/service/GetExtraIntroCandidatesService.kt` + `query/service/port/in/GetExtraIntroCandidatesUseCase.kt`
- `query/dao/GetExtraIntroCandidateDao.kt` + `query/dao/dto/ExtraIntroCandidate.kt`, `ExtraIntroCandidates.kt`, `ExtraIntroScoringRow.kt`
- `common/lock/LockKeyConstraints.kt` (EXTRA_INTRO 상수 추가)

**meeple-infra / solomatch**
- `query/GetExtraIntroCandidateDaoImpl.kt`
- `command/adapter/ExtraIntroCandidateAdapter.kt`
- `region/GetRegionProximityAdapter.kt` (core out-port → RegionProximityRegistry)

**meeple-api**
- `match/ExtraIntroController.kt` (또는 SoloMatchController에 엔드포인트 추가)
- `match/response/ExtraIntroCandidatesResponse.kt`, `match/response/ExtraIntroResponse.kt`

**테스트 (meeple-api test)**
- `matching/MatchSelectorTest.kt` (Kotest 유닛)
- `match/ExtraIntroCandidatesIntegrationTest.kt`, `match/ExtraIntroIntegrationTest.kt` (E2E)

---

## Task 1: `meeple-matching` 모듈 생성 + 알고리즘 이동

**Files:**
- Create: `meeple-matching/build.gradle.kts`
- Modify: `settings.gradle`
- Create: `meeple-matching/src/main/kotlin/com/org/meeple/matching/MatchScoringProfile.kt`
- Create: `meeple-matching/src/main/kotlin/com/org/meeple/matching/MatchScorer.kt`
- Delete: `meeple-scheduler/.../solomatch/command/domain/MatchScorer.kt`, `meeple-scheduler/.../solomatch/query/dto/MatchScoringProfile.kt`

**Interfaces:**
- Produces: `com.org.meeple.matching.MatchScoringProfile`(기존 필드 동일), `com.org.meeple.matching.MatchScorer`(모든 함수 유지 + `fun <T> orderByScore(scored: List<Pair<T, Double>>, random: Random): List<T>`).

- [ ] **Step 1: settings.gradle에 모듈 등록**

`settings.gradle`의 include 목록에 추가:
```groovy
include("meeple-matching")
```

- [ ] **Step 2: build.gradle.kts 작성**

`meeple-matching/build.gradle.kts`:
```kotlin
plugins {
	id("meeple.kotlin-conventions")
}

dependencies {
	// 순수 매칭 알고리즘. 공용 enum만 참조하고 프레임워크·인프라에 의존하지 않는다.
	implementation(project(":meeple-common"))
}
```

- [ ] **Step 3: MatchScoringProfile 이동**

`meeple-matching/.../matching/MatchScoringProfile.kt` — 기존 scheduler `query/dto/MatchScoringProfile.kt` 내용을 그대로 옮기되 package를 `com.org.meeple.matching`으로 변경. (필드/주석 동일)

- [ ] **Step 4: MatchScorer 이동 + orderByScore 제네릭화**

`meeple-matching/.../matching/MatchScorer.kt`:
```kotlin
package com.org.meeple.matching

import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 매칭의 이상형 우선순위 점수 계산기(순수). 이상형은 필터가 아니라 우선순위이므로,
 * 부합하지 않아도 점수만 낮아진다. 이상형 부합(양방향)·거리·최근을 0~1로 정규화해 가중 합산한다.
 * 일일 배치와 실시간 추가 소개가 공유한다.
 */
object MatchScorer {

	private const val IDEAL_WEIGHT: Double = 0.4
	private const val DISTANCE_WEIGHT: Double = 0.4
	private const val RECENCY_WEIGHT: Double = 0.2

	/** 종합 점수 버킷 크기. 같은 버킷(≈동점)은 무작위로 섞는다. */
	private const val BUCKET_SIZE: Double = 0.05

	fun mutualIdealFit(target: MatchScoringProfile?, candidate: MatchScoringProfile?): Double =
		(directionFit(target, candidate) + directionFit(candidate, target)) / 2.0

	fun distanceScore(rank: Int?, regionCount: Int): Double {
		if (rank == null) return 0.0
		if (regionCount <= 1) return 1.0
		return 1.0 - rank.toDouble() / (regionCount - 1)
	}

	fun recencyScore(lastLoginAt: LocalDateTime, loginAfter: LocalDateTime, now: LocalDateTime): Double {
		val windowSeconds: Long = Duration.between(loginAfter, now).seconds
		if (windowSeconds <= 0) return 1.0
		val elapsedSeconds: Long = Duration.between(loginAfter, lastLoginAt).seconds
		return (elapsedSeconds.toDouble() / windowSeconds).coerceIn(0.0, 1.0)
	}

	fun combinedScore(idealFit: Double, distanceScore: Double, recencyScore: Double): Double =
		IDEAL_WEIGHT * idealFit + DISTANCE_WEIGHT * distanceScore + RECENCY_WEIGHT * recencyScore

	/**
	 * 후보를 종합 점수 내림차순으로 정렬하되, [BUCKET_SIZE] 단위 버킷 안은 [random]으로 섞는다.
	 * (상위 동점군 내 무작위 — 같은 상대만 반복 노출되지 않게 하면서 이상형 우선순위는 유지)
	 */
	fun <T> orderByScore(scored: List<Pair<T, Double>>, random: Random): List<T> =
		scored
			.groupBy { (_, score: Double) -> (score / BUCKET_SIZE).toInt() }
			.entries
			.sortedByDescending { entry: Map.Entry<Int, List<Pair<T, Double>>> -> entry.key }
			.flatMap { entry: Map.Entry<Int, List<Pair<T, Double>>> ->
				entry.value.shuffled(random).map { (item: T, _) -> item }
			}

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

- [ ] **Step 5: scheduler 원본 삭제 + import 교체(임시 컴파일 오류 허용)**

scheduler의 `command/domain/MatchScorer.kt`, `query/dto/MatchScoringProfile.kt` 삭제. `MatchScoringProfile`/`MatchScorer`를 참조하는 scheduler 파일들(`SoloMatchBatchService`, `GetMatchScoringProfileDao`, `query/dto` 소비처)의 import를 `com.org.meeple.matching.*`로 교체. (Task 1은 Task 2에서 함께 컴파일 검증)

- [ ] **Step 6: scheduler build.gradle에 의존 추가**

`meeple-scheduler/build.gradle`:
```groovy
	implementation(project(":meeple-matching"))
```

- [ ] **Step 7: infra build.gradle에 의존 추가**

`meeple-infra/build.gradle` dependencies에 추가(무브된 `MatchScoringProfile`을 infra의 `GetMatchScoringProfileDaoImpl`이 참조):
```groovy
	implementation(project(":meeple-matching"))
```

> 커밋은 Task 2와 함께(중간 상태가 컴파일되지 않을 수 있음).

---

## Task 2: MatchableUser → ScoringCandidate + MatchSelector + 배치 리팩터링

**Files:**
- Create: `meeple-matching/src/main/kotlin/com/org/meeple/matching/ScoringCandidate.kt`
- Create: `meeple-matching/src/main/kotlin/com/org/meeple/matching/MatchSelector.kt`
- Modify: `meeple-scheduler/.../solomatch/query/dto/MatchableUser.kt`
- Modify: `meeple-scheduler/.../solomatch/command/application/SoloMatchBatchService.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/matching/MatchSelectorTest.kt`

**Interfaces:**
- Consumes: `MatchScorer`, `MatchScoringProfile` (Task 1).
- Produces:
  - `interface ScoringCandidate { val userId: Long; val regionId: Long; val lastLoginAt: LocalDateTime }`
  - `MatchSelector.selectBest(...)`, `MatchSelector.orderByScore(...)` (아래 시그니처).

- [ ] **Step 1: ScoringCandidate 인터페이스 작성**

`meeple-matching/.../matching/ScoringCandidate.kt`:
```kotlin
package com.org.meeple.matching

import java.time.LocalDateTime

/**
 * 종합 점수 계산에 필요한 후보의 최소 계약. (거리=지역, 최근성=마지막 로그인)
 * 배치의 [MatchableUser], 추가 소개의 후보 행이 이를 구현해 [MatchSelector]를 공유한다.
 */
interface ScoringCandidate {
	val userId: Long
	val regionId: Long
	val lastLoginAt: LocalDateTime
}
```

- [ ] **Step 2: MatchSelector 작성**

`meeple-matching/.../matching/MatchSelector.kt`:
```kotlin
package com.org.meeple.matching

import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 이상형·거리·최근 종합 점수로 후보를 정렬(동점군 무작위)하고 선택하는 순수 로직.
 * 일일 배치와 실시간 추가 소개가 공유한다. 거리 근접 순위(regionRankByRegionId)는
 * 호출부가 미리 계산해 넘긴다(배치=RegionProximityPort, 추가소개=GetRegionProximityPort).
 */
object MatchSelector {

	/**
	 * 후보를 종합 점수순(동점군 무작위)으로 정렬해 반환한다. (조회 상위 N 등 재소개 필터가 필요 없을 때)
	 */
	fun <T : ScoringCandidate> orderByScore(
		targetProfile: MatchScoringProfile?,
		candidates: List<T>,
		profileOf: (T) -> MatchScoringProfile?,
		regionRankByRegionId: Map<Long, Int>,
		regionCount: Int,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
		random: Random,
	): List<T> {
		val scored: List<Pair<T, Double>> = candidates.map { candidate: T ->
			candidate to score(candidate, targetProfile, profileOf, regionRankByRegionId, regionCount, now, loginAfter)
		}
		return MatchScorer.orderByScore(scored, random)
	}

	/**
	 * 정렬된 후보 중 [isExcluded]가 false인 최고점 후보 1명을 반환한다. 없으면 null.
	 * (재소개 이력이 있는 후보를 건너뛴다)
	 */
	fun <T : ScoringCandidate> selectBest(
		targetProfile: MatchScoringProfile?,
		candidates: List<T>,
		profileOf: (T) -> MatchScoringProfile?,
		regionRankByRegionId: Map<Long, Int>,
		regionCount: Int,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
		random: Random,
		isExcluded: (T) -> Boolean,
	): T? =
		orderByScore(targetProfile, candidates, profileOf, regionRankByRegionId, regionCount, now, loginAfter, random)
			.firstOrNull { candidate: T -> !isExcluded(candidate) }

	private fun <T : ScoringCandidate> score(
		candidate: T,
		targetProfile: MatchScoringProfile?,
		profileOf: (T) -> MatchScoringProfile?,
		regionRankByRegionId: Map<Long, Int>,
		regionCount: Int,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
	): Double {
		val idealFit: Double = MatchScorer.mutualIdealFit(targetProfile, profileOf(candidate))
		val distanceScore: Double = MatchScorer.distanceScore(regionRankByRegionId[candidate.regionId], regionCount)
		val recencyScore: Double = MatchScorer.recencyScore(candidate.lastLoginAt, loginAfter, now)
		return MatchScorer.combinedScore(idealFit, distanceScore, recencyScore)
	}
}
```

- [ ] **Step 3: MatchableUser가 ScoringCandidate 구현**

`meeple-scheduler/.../solomatch/query/dto/MatchableUser.kt`:
```kotlin
package com.org.meeple.scheduler.solomatch.query.dto

import com.org.meeple.common.user.Gender
import com.org.meeple.matching.ScoringCandidate
import java.time.LocalDateTime

/**
 * 일일 배치의 대상이자 후보가 되는 활성 매칭 유저 read model.
 * 버킷 키(성별·지역)와 후보 정렬(최근 로그인)에 쓰므로 모두 non-null이다.
 */
data class MatchableUser(
	override val userId: Long,
	val gender: Gender,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
) : ScoringCandidate
```

- [ ] **Step 4: SoloMatchBatchService가 MatchSelector 사용**

`SoloMatchBatchService.findBestFreshPartner`를 다음으로 교체(동작 동일). import에서 `MatchScorer` 제거, `com.org.meeple.matching.MatchScoringProfile`, `com.org.meeple.matching.MatchSelector` 추가:
```kotlin
	private fun findBestFreshPartner(
		target: MatchableUser,
		pool: MatchPool,
		profiles: Map<Long, MatchScoringProfile>,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
	): MatchableUser? {
		val partnerGender: Gender = target.gender.opposite()
		val nearby: List<Long> = regionProximityPort.nearbyRegionIds(target.regionId)
		val rankByRegion: Map<Long, Int> = nearby.withIndex().associate { (index: Int, regionId: Long) -> regionId to index }

		return MatchSelector.selectBest(
			targetProfile = profiles[target.userId],
			candidates = pool.availableCandidates(partnerGender),
			profileOf = { candidate: MatchableUser -> profiles[candidate.userId] },
			regionRankByRegionId = rankByRegion,
			regionCount = nearby.size,
			now = now,
			loginAfter = loginAfter,
			random = random,
			isExcluded = { candidate: MatchableUser -> getMatchRecordDao.existsByPair(target.userId, candidate.userId) },
		)
	}
```

- [ ] **Step 5: 전체 컴파일**

Run: `./gradlew :meeple-matching:compileKotlin :meeple-scheduler:compileKotlin :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: MatchSelector 유닛 테스트 작성**

`meeple-api/src/test/kotlin/com/org/meeple/matching/MatchSelectorTest.kt`:
```kotlin
package com.org.meeple.matching

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlin.random.Random

private data class Cand(
	override val userId: Long,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
) : ScoringCandidate

class MatchSelectorTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)
	val loginAfter: LocalDateTime = now.minusWeeks(2)
	// 같은 지역(rank 0)·최근 로그인일수록 점수가 높다. 이상형은 프로필 null → 중립(1.0).
	val near = Cand(userId = 1L, regionId = 10L, lastLoginAt = now)
	val far = Cand(userId = 2L, regionId = 99L, lastLoginAt = loginAfter)
	val rank: Map<Long, Int> = mapOf(10L to 0, 99L to 1)

	describe("selectBest") {
		it("가장 높은 점수(가까운 지역·최근)의 후보를 고른다") {
			val picked = MatchSelector.selectBest(
				targetProfile = null,
				candidates = listOf(far, near),
				profileOf = { null },
				regionRankByRegionId = rank,
				regionCount = 2,
				now = now,
				loginAfter = loginAfter,
				random = Random(0),
				isExcluded = { false },
			)
			picked?.userId shouldBe 1L
		}

		it("최고점 후보가 제외되면 다음 후보를 고른다") {
			val picked = MatchSelector.selectBest(
				targetProfile = null,
				candidates = listOf(far, near),
				profileOf = { null },
				regionRankByRegionId = rank,
				regionCount = 2,
				now = now,
				loginAfter = loginAfter,
				random = Random(0),
				isExcluded = { c -> c.userId == 1L },
			)
			picked?.userId shouldBe 2L
		}

		it("모든 후보가 제외되면 null") {
			val picked = MatchSelector.selectBest(
				targetProfile = null,
				candidates = listOf(far, near),
				profileOf = { null },
				regionRankByRegionId = rank,
				regionCount = 2,
				now = now,
				loginAfter = loginAfter,
				random = Random(0),
				isExcluded = { true },
			)
			picked.shouldBeNull()
		}
	}
})
```

- [ ] **Step 7: 유닛 테스트 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.matching.MatchSelectorTest"`
Expected: PASS

- [ ] **Step 8: 배치 회귀 테스트 실행**

Run: `./gradlew :meeple-api:test --tests "*SoloMatchBatch*"`
Expected: PASS (배치 동작 불변)

- [ ] **Step 9: 커밋**

```bash
git add meeple-matching settings.gradle meeple-scheduler meeple-infra/build.gradle meeple-api/src/test/kotlin/com/org/meeple/matching
git commit -m "refactor(match): 매칭 스코어링·선택 알고리즘을 meeple-matching 모듈로 분리

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: common enum + core 에러코드/락키 추가

**Files:**
- Modify: `meeple-common/.../coin/CoinUsageType.kt`
- Modify: `meeple-common/.../match/SoloMatchType.kt`
- Modify: `meeple-core/.../solomatch/MatchErrorCode.kt`
- Modify: `meeple-core/.../common/lock/LockKeyConstraints.kt`

**Interfaces:**
- Produces: `CoinUsageType.EXTRA_INTRO`(30), `SoloMatchType.EXTRA`, `MatchErrorCode.EXTRA_INTRO_NO_CANDIDATE`, `MatchErrorCode.MATCH_USER_NOT_MATCHABLE`, `LockKeyConstraints.EXTRA_INTRO`.

- [ ] **Step 1: CoinUsageType에 EXTRA_INTRO 추가**

`CoinUsageType.kt`의 enum 목록에 추가:
```kotlin
	/** 추가 소개(오늘의 추천 외 1명 더 소개받기). */
	EXTRA_INTRO("추가 소개", 30),
```

- [ ] **Step 2: SoloMatchType에 EXTRA 추가**

`SoloMatchType.kt`에 추가:
```kotlin
	/** 사용자가 코인으로 추가 소개받아 생성된 소개. */
	EXTRA("추가 소개"),
```

- [ ] **Step 3: MatchErrorCode 확인 후 추가**

먼저 파일을 읽어 기존 형식(HttpStatus·code·message 필드 구성)을 확인하고 동일 형식으로 두 항목 추가:
```kotlin
	// 추가 소개: 소개 가능한 후보가 없음(코인 미차감).
	EXTRA_INTRO_NO_CANDIDATE(HttpStatus.NOT_FOUND, "소개 가능한 상대가 없습니다."),
	// 추가 소개: 요청자가 아직 매칭 가능 상태가 아님(읽기 모델 미적재).
	MATCH_USER_NOT_MATCHABLE(HttpStatus.BAD_REQUEST, "매칭 가능한 상태가 아닙니다."),
```
(각 필드 이름/순서는 기존 enum 시그니처에 맞춘다. 기존에 유사 코드가 있으면 재사용하고 중복 추가하지 않는다.)

- [ ] **Step 4: LockKeyConstraints에 EXTRA_INTRO 추가**

파일을 읽어 기존 상수 형식(예: `const val MATCH_INTEREST = "MATCH_INTEREST"`)을 확인하고 동일 형식으로:
```kotlin
	const val EXTRA_INTRO = "EXTRA_INTRO"
```

- [ ] **Step 5: 컴파일**

Run: `./gradlew :meeple-common:compileKotlin :meeple-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add meeple-common meeple-core/src/main/kotlin/com/org/meeple/core/solomatch/MatchErrorCode.kt meeple-core/src/main/kotlin/com/org/meeple/core/common/lock/LockKeyConstraints.kt
git commit -m "feat(match): 추가 소개용 코인 타입·매칭 타입·에러코드 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: core 조회 경로 (read models + DAO 포트 + query 서비스)

**Files:**
- Create: `meeple-core/.../solomatch/query/dao/dto/ExtraIntroCandidate.kt`
- Create: `meeple-core/.../solomatch/query/dao/dto/ExtraIntroCandidates.kt`
- Create: `meeple-core/.../solomatch/query/dao/dto/ExtraIntroScoringRow.kt`
- Create: `meeple-core/.../solomatch/query/dao/GetExtraIntroCandidateDao.kt`
- Create: `meeple-core/.../common/region/GetRegionProximityPort.kt`
- Create: `meeple-core/.../solomatch/query/service/port/in/GetExtraIntroCandidatesUseCase.kt`
- Create: `meeple-core/.../solomatch/query/service/GetExtraIntroCandidatesService.kt`

**Interfaces:**
- Consumes: `MatchScoringProfile`, `MatchSelector`, `ScoringCandidate` (matching); `GetMatchUserUseCase.findByUserId` (matchuser); `TimeGenerator`.
- Produces:
  - `data class ExtraIntroScoringRow(override val userId, override val regionId, override val lastLoginAt, val profile: MatchScoringProfile?) : ScoringCandidate`
  - `data class ExtraIntroCandidate(...표시 필드...)`
  - `data class ExtraIntroCandidates(val totalCount: Int, val candidates: List<ExtraIntroCandidate>)`
  - `GetExtraIntroCandidateDao { fun findScoringRows(requesterId, gender, loginAfter): List<ExtraIntroScoringRow>; fun findRequesterProfile(requesterId, today): MatchScoringProfile?; fun findDisplayProfiles(userIds): List<ExtraIntroCandidate> }`
  - `GetRegionProximityPort { fun nearbyRegionIds(regionId): List<Long> }`
  - `GetExtraIntroCandidatesUseCase { fun getCandidates(userId): ExtraIntroCandidates }`

- [ ] **Step 1: read model DTO 3종 작성**

`ExtraIntroScoringRow.kt` — `GetExtraIntroCandidateDao`가 반환하는 경량 스코어링 행. `MatchWithPartner`의 프로필 필드는 표시용(`ExtraIntroCandidate`)에만 두고 여기엔 스코어링 입력만 둔다.
```kotlin
package com.org.meeple.core.solomatch.query.dao.dto

import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.matching.ScoringCandidate
import java.time.LocalDateTime

/** 추가 소개 자격 후보의 경량 스코어링 행. (전체 수 계산 + 정렬용, 표시 프로필은 별도 적재) */
data class ExtraIntroScoringRow(
	override val userId: Long,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
	val profile: MatchScoringProfile?,
) : ScoringCandidate
```

`ExtraIntroCandidate.kt` — 표시용 프로필. `MatchWithPartner`의 프로필 부분을 참고해 필드 구성:
```kotlin
package com.org.meeple.core.solomatch.query.dao.dto

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import java.time.LocalDate

/** 추가 소개 후보 표시용 read model. (닉네임 블러 등 노출 정책은 프론트가 처리) */
data class ExtraIntroCandidate(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val birthday: LocalDate?,
	val height: Int?,
	val gender: Gender?,
	val job: String?,
	val activityArea: String?,
	val introduction: String?,
	val companyName: String?,
	val universityName: String?,
	val traits: List<String>,
	val interests: List<String>,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val religion: Religion?,
	val drinkingStatus: DrinkingStatus?,
	val bodyType: BodyType?,
)
```

`ExtraIntroCandidates.kt`:
```kotlin
package com.org.meeple.core.solomatch.query.dao.dto

/** 추가 소개 후보 조회 결과. [totalCount]는 전체 자격 후보 수, [candidates]는 점수 상위 표시 목록. */
data class ExtraIntroCandidates(
	val totalCount: Int,
	val candidates: List<ExtraIntroCandidate>,
)
```

- [ ] **Step 2: GetExtraIntroCandidateDao 포트 작성**

`GetExtraIntroCandidateDao.kt`:
```kotlin
package com.org.meeple.core.solomatch.query.dao

import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroCandidate
import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroScoringRow
import com.org.meeple.matching.MatchScoringProfile
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 추가 소개 자격 후보 조회 dao. 자격 = 반대 성별 · 최근 로그인 · 재소개 이력 없음 · 매칭 가능(match_user 존재).
 * 전체 수 계산·정렬은 경량 행([findScoringRows])으로, 표시 프로필은 상위 N명만([findDisplayProfiles]) 적재한다.
 */
interface GetExtraIntroCandidateDao {

	/** 요청자([requesterId])의 자격 후보 경량 스코어링 행 전체. (재소개 이력 있는 후보 제외) */
	fun findScoringRows(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime): List<ExtraIntroScoringRow>

	/** 요청자 자신의 스코어링 프로필. (양방향 이상형 부합 계산용, [today] 기준 나이) */
	fun findRequesterProfile(requesterId: Long, today: LocalDate): MatchScoringProfile?

	/** 주어진 userId들의 표시 프로필. (호출부가 점수순 정렬을 유지) */
	fun findDisplayProfiles(userIds: List<Long>): List<ExtraIntroCandidate>
}
```

- [ ] **Step 3: GetRegionProximityPort 작성**

`meeple-core/.../common/region/GetRegionProximityPort.kt`:
```kotlin
package com.org.meeple.core.common.region

/**
 * 지역 근접 순위 조회 out-port. [regionId]에서 가까운 순으로 정렬된 전체 regionId를 반환한다.
 * infra가 RegionProximityRegistry(온보딩·배치와 공유 스냅샷)에 위임해 구현한다.
 */
interface GetRegionProximityPort {
	fun nearbyRegionIds(regionId: Long): List<Long>
}
```

- [ ] **Step 4: UseCase in-port 작성**

`query/service/port/in/GetExtraIntroCandidatesUseCase.kt`:
```kotlin
package com.org.meeple.core.solomatch.query.service.port.`in`

import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroCandidates

/** 추가 소개 자격 후보(상위 N명 + 전체 수) 조회 유스케이스. */
interface GetExtraIntroCandidatesUseCase {
	fun getCandidates(userId: Long): ExtraIntroCandidates
}
```

- [ ] **Step 5: GetExtraIntroCandidatesService 작성**

`query/service/GetExtraIntroCandidatesService.kt`:
```kotlin
package com.org.meeple.core.solomatch.query.service

import com.org.meeple.core.common.region.GetRegionProximityPort
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.matchuser.command.application.port.`in`.GetMatchUserUseCase
import com.org.meeple.core.matchuser.command.domain.MatchUser
import com.org.meeple.core.solomatch.query.dao.GetExtraIntroCandidateDao
import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroCandidate
import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroCandidates
import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroScoringRow
import com.org.meeple.core.solomatch.query.service.port.`in`.GetExtraIntroCandidatesUseCase
import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.matching.MatchSelector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [GetExtraIntroCandidatesUseCase] 구현. 자격 후보를 종합 점수로 정렬해 상위 [DISPLAY_LIMIT]명의 표시 프로필과
 * 전체 자격 후보 수를 반환한다. 부수효과 없는 순수 조회다.
 */
@Service
@Transactional(readOnly = true)
class GetExtraIntroCandidatesService(
	private val getMatchUserUseCase: GetMatchUserUseCase,
	private val getExtraIntroCandidateDao: GetExtraIntroCandidateDao,
	private val getRegionProximityPort: GetRegionProximityPort,
	private val timeGenerator: TimeGenerator,
	private val random: Random = Random.Default,
) : GetExtraIntroCandidatesUseCase {

	override fun getCandidates(userId: Long): ExtraIntroCandidates {
		// 매칭 가능 상태가 아니면 후보도 없다. (읽기 모델 미적재)
		val requester: MatchUser = getMatchUserUseCase.findByUserId(userId)
			?: return ExtraIntroCandidates(totalCount = 0, candidates = emptyList())

		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
		val today: LocalDate = now.toLocalDate()

		val rows: List<ExtraIntroScoringRow> =
			getExtraIntroCandidateDao.findScoringRows(userId, requester.partnerGender(), loginAfter)
		if (rows.isEmpty()) return ExtraIntroCandidates(totalCount = 0, candidates = emptyList())

		val requesterProfile: MatchScoringProfile? = getExtraIntroCandidateDao.findRequesterProfile(userId, today)
		val nearby: List<Long> = getRegionProximityPort.nearbyRegionIds(requester.regionId)
		val rankByRegion: Map<Long, Int> = nearby.withIndex().associate { (index: Int, regionId: Long) -> regionId to index }

		val ordered: List<ExtraIntroScoringRow> = MatchSelector.orderByScore(
			targetProfile = requesterProfile,
			candidates = rows,
			profileOf = { row: ExtraIntroScoringRow -> row.profile },
			regionRankByRegionId = rankByRegion,
			regionCount = nearby.size,
			now = now,
			loginAfter = loginAfter,
			random = random,
		)

		val topUserIds: List<Long> = ordered.take(DISPLAY_LIMIT).map { row: ExtraIntroScoringRow -> row.userId }
		val profileByUserId: Map<Long, ExtraIntroCandidate> =
			getExtraIntroCandidateDao.findDisplayProfiles(topUserIds).associateBy { it.userId }
		// 점수 정렬 순서를 유지한다.
		val candidates: List<ExtraIntroCandidate> = topUserIds.mapNotNull { id: Long -> profileByUserId[id] }

		return ExtraIntroCandidates(totalCount = rows.size, candidates = candidates)
	}

	companion object {
		/** 응답으로 내려주는 후보 프로필 수. */
		private const val DISPLAY_LIMIT = 11
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
```

- [ ] **Step 6: 컴파일**

Run: `./gradlew :meeple-core:compileKotlin`
Expected: BUILD SUCCESSFUL

> 커밋은 Task 5(command)까지 core를 완성한 뒤 함께 하거나 여기서 단독 커밋 가능:
```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/solomatch/query meeple-core/src/main/kotlin/com/org/meeple/core/common/region
git commit -m "feat(match): 추가 소개 자격 후보 조회 유스케이스·포트 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: core 명령 경로 (추가 소개 UseCase/Service + out-port)

**Files:**
- Create: `meeple-core/.../solomatch/command/application/port/in/IntroduceExtraMatchUseCase.kt`
- Create: `meeple-core/.../solomatch/command/application/port/out/GetExtraIntroCandidatePort.kt` (+ `ExtraIntroCandidateRow`)
- Create: `meeple-core/.../solomatch/command/application/IntroduceExtraMatchService.kt`

**Interfaces:**
- Consumes: `MatchSelector`, `MatchScoringProfile` (matching); `GetMatchUserUseCase`; `SpendCoinUseCase.spend(userId, SpendCoinCommand(amount, coinUsageType))`; `SaveMatchPort.save(match)`; `GetRegionProximityPort`; `Match.propose(...)`; `TimeGenerator`; `@DistributedLock`.
- Produces:
  - `data class ExtraIntroCandidateRow(override val userId, override val regionId, override val lastLoginAt, val profile: MatchScoringProfile?) : ScoringCandidate`
  - `GetExtraIntroCandidatePort { fun findCandidates(requesterId, partnerGender, loginAfter): List<ExtraIntroCandidateRow>; fun findRequesterProfile(requesterId, today): MatchScoringProfile?; fun existsIntroduced(requesterId, candidateId): Boolean }`
  - `IntroduceExtraMatchUseCase { fun introduce(userId): Match }`

- [ ] **Step 1: out-port + row 작성**

`command/application/port/out/GetExtraIntroCandidatePort.kt`:
```kotlin
package com.org.meeple.core.solomatch.command.application.port.out

import com.org.meeple.common.user.Gender
import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.matching.ScoringCandidate
import java.time.LocalDate
import java.time.LocalDateTime

/** 추가 소개 명령용 후보 행. (조회 경로와 별개로 command가 자체 소유 — CQRS) */
data class ExtraIntroCandidateRow(
	override val userId: Long,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
	val profile: MatchScoringProfile?,
) : ScoringCandidate

/**
 * 추가 소개 후보 조회 out-port. 자격 = 반대 성별 · 최근 로그인 · 매칭 가능(match_user 존재).
 * 재소개 제외는 선택 단계에서 [existsIntroduced]로 판정한다. (배치의 existsByPair와 동일한 memberKey 기준)
 */
interface GetExtraIntroCandidatePort {
	fun findCandidates(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime): List<ExtraIntroCandidateRow>
	fun findRequesterProfile(requesterId: Long, today: LocalDate): MatchScoringProfile?
	fun existsIntroduced(requesterId: Long, candidateId: Long): Boolean
}
```

- [ ] **Step 2: UseCase in-port 작성**

`command/application/port/in/IntroduceExtraMatchUseCase.kt`:
```kotlin
package com.org.meeple.core.solomatch.command.application.port.`in`

import com.org.meeple.core.solomatch.command.domain.Match

/** 추가 소개 유스케이스. 코인 차감 후 자격 후보 1명을 골라 PROPOSED 매칭을 만든다. */
interface IntroduceExtraMatchUseCase {
	fun introduce(userId: Long): Match
}
```

- [ ] **Step 3: IntroduceExtraMatchService 작성**

`command/application/IntroduceExtraMatchService.kt`:
```kotlin
package com.org.meeple.core.solomatch.command.application

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.common.region.GetRegionProximityPort
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.matchuser.command.application.port.`in`.GetMatchUserUseCase
import com.org.meeple.core.matchuser.command.domain.MatchUser
import com.org.meeple.core.solomatch.MatchErrorCode
import com.org.meeple.core.solomatch.command.application.port.`in`.IntroduceExtraMatchUseCase
import com.org.meeple.core.solomatch.command.application.port.out.ExtraIntroCandidateRow
import com.org.meeple.core.solomatch.command.application.port.out.GetExtraIntroCandidatePort
import com.org.meeple.core.solomatch.command.application.port.out.SaveMatchPort
import com.org.meeple.core.solomatch.command.domain.Match
import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.matching.MatchSelector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [IntroduceExtraMatchUseCase] 구현. 요청자의 자격 후보를 이상형·거리·최근 종합 점수([MatchSelector])로
 * 정렬해 재소개 이력 없는 최고점 후보 1명을 고른다. 후보가 있으면 추가 소개 코인([CoinUsageType.EXTRA_INTRO])을
 * 차감하고 [SoloMatchType.EXTRA] PROPOSED 매칭을 만든다. 후보가 없으면 코인을 차감하지 않고 예외.
 *
 * 코인 차감·매칭 저장은 같은 트랜잭션이라 저장 실패(유니크 위반 등) 시 차감도 롤백된다.
 * 요청자별 분산 락([LockKeyConstraints.EXTRA_INTRO])으로 더블클릭 이중 과금을 fail-fast(waitTime=0)로 막는다.
 */
@Service
class IntroduceExtraMatchService(
	private val getMatchUserUseCase: GetMatchUserUseCase,
	private val getExtraIntroCandidatePort: GetExtraIntroCandidatePort,
	private val getRegionProximityPort: GetRegionProximityPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val saveMatchPort: SaveMatchPort,
	private val timeGenerator: TimeGenerator,
	private val random: Random = Random.Default,
) : IntroduceExtraMatchUseCase {

	@DistributedLock(prefix = LockKeyConstraints.EXTRA_INTRO, keys = ["#userId"], waitTime = 0)
	@Transactional
	override fun introduce(userId: Long): Match {
		val requester: MatchUser = getMatchUserUseCase.findByUserId(userId)
			?: throw BusinessException(MatchErrorCode.MATCH_USER_NOT_MATCHABLE)

		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
		val today: LocalDate = now.toLocalDate()

		val candidates: List<ExtraIntroCandidateRow> =
			getExtraIntroCandidatePort.findCandidates(userId, requester.partnerGender(), loginAfter)
		val requesterProfile: MatchScoringProfile? = getExtraIntroCandidatePort.findRequesterProfile(userId, today)
		val nearby: List<Long> = getRegionProximityPort.nearbyRegionIds(requester.regionId)
		val rankByRegion: Map<Long, Int> = nearby.withIndex().associate { (index: Int, regionId: Long) -> regionId to index }

		val partner: ExtraIntroCandidateRow = MatchSelector.selectBest(
			targetProfile = requesterProfile,
			candidates = candidates,
			profileOf = { row: ExtraIntroCandidateRow -> row.profile },
			regionRankByRegionId = rankByRegion,
			regionCount = nearby.size,
			now = now,
			loginAfter = loginAfter,
			random = random,
			isExcluded = { row: ExtraIntroCandidateRow -> getExtraIntroCandidatePort.existsIntroduced(userId, row.userId) },
		) ?: throw BusinessException(MatchErrorCode.EXTRA_INTRO_NO_CANDIDATE)

		// 후보를 확정한 뒤에만 차감한다. (후보 없으면 차감 없음)
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = CoinUsageType.EXTRA_INTRO.coinAmount, coinUsageType = CoinUsageType.EXTRA_INTRO))

		val match: Match = Match.propose(
			requesterId = requester.userId,
			requesterGender = requester.gender,
			partnerId = partner.userId,
			matchType = SoloMatchType.EXTRA,
			now = now,
		)
		return saveMatchPort.save(match)
	}

	companion object {
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
```

- [ ] **Step 4: Match.propose 시그니처 확인**

`Match.kt`의 `propose` 파라미터 이름/순서가 위 호출(`requesterId, requesterGender, partnerId, matchType, now`)과 일치하는지 확인. 불일치 시 호출부를 실제 시그니처에 맞춘다(`RecommendMatchService`의 호출을 참고).

- [ ] **Step 5: 컴파일**

Run: `./gradlew :meeple-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/solomatch
git commit -m "feat(match): 추가 소개 명령 유스케이스·후보 조회 포트 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: infra 어댑터 (조회 DAO + 명령 어댑터 + 지역 근접)

**Files:**
- Create: `meeple-infra/.../solomatch/query/GetExtraIntroCandidateDaoImpl.kt`
- Create: `meeple-infra/.../solomatch/command/adapter/ExtraIntroCandidateAdapter.kt`
- Create: `meeple-infra/.../region/GetRegionProximityAdapter.kt`

**Interfaces:**
- Consumes: `GetExtraIntroCandidateDao`, `GetExtraIntroCandidatePort`, `GetRegionProximityPort` (core); `RegionProximityRegistry` (infra); QueryDSL Q타입(`QUserDetailEntity`, `QUserIdealTypeEntity`, `QSoloMatchEntity`, `QSoloMatchMemberEntity`, `QRegionEntity`, `QMatchUserEntity`).
- Produces: 위 3개 포트의 Spring 빈 구현.

- [ ] **Step 1: 엔티티/필드 확인**

먼저 다음을 읽어 실제 필드명·테이블명·Q타입을 확인한다:
- `GetMatchScoringProfileDaoImpl.kt` (user_details + user_ideal_types 조인, 나이 계산, 결측 처리 패턴 재사용)
- `GetMatchableUserDaoImpl.kt` (match_user 기준 최근 로그인 조회 + 인덱스)
- `GetMatchWithPartnerDaoImpl.kt` (표시 프로필 투영 + region 조인 + traits/interests `Expressions.path`)
- `MatchAdapter.existsByPair` 또는 `GetMatchRecordDaoImpl`의 memberKey 재소개 판정 native query
- `MatchUserEntity`(match_user)의 gender/regionId/lastLoginAt 필드

- [ ] **Step 2: GetExtraIntroCandidateDaoImpl 작성 (query)**

`meeple-infra/.../solomatch/query/GetExtraIntroCandidateDaoImpl.kt`. 핵심 규칙:
- `findScoringRows`: `match_user` 기준(반대 성별·최근 로그인·본인 제외) 후보를, `user_details`·`user_ideal_types` **명시적 조인**으로 스코어링 필드 투영. 재소개 제외는 `solo_matches.member_key`에 요청자-후보 조합이 없다는 NOT EXISTS 서브쿼리(소프트 삭제 포함이라 `@SQLRestriction` 우회를 위해 native 서브쿼리 또는 memberKey 계산식 사용 — `GetMatchRecordDaoImpl.existsByPair` 방식 참고).
- 나이: `birthday`→`today` 기준 계산(무연산 read model), `GetMatchScoringProfileDaoImpl` 동일 로직.
- `findRequesterProfile`: 동일 조인으로 요청자 1명의 `MatchScoringProfile` 투영.
- `findDisplayProfiles(userIds)`: `user_details` + `region` 조인으로 `ExtraIntroCandidate` 투영. `traits`/`interests`는 `Expressions.path(List::class.java, userDetail, "traits")` 방식.
- `@Component`, `JPAQueryFactory`만 주입.

(구현은 위 참고 파일들의 QueryDSL 패턴을 그대로 따른다. 콤마 암묵 조인 금지, `join … on` 명시.)

- [ ] **Step 3: ExtraIntroCandidateAdapter 작성 (command out-port)**

`meeple-infra/.../solomatch/command/adapter/ExtraIntroCandidateAdapter.kt` — `GetExtraIntroCandidatePort` 구현.
- `findCandidates`/`findRequesterProfile`: query DAO와 동일 조회지만 CQRS상 명령용은 별도 구현(같은 조회라도 공유하지 않음). QueryDSL로 `ExtraIntroCandidateRow` 투영. (infra 내부 재사용을 위해 private 헬퍼는 가능하나 포트는 각자 소유)
- `existsIntroduced`: 기존 `MatchAdapter`/`GetMatchRecordDaoImpl`의 memberKey 기반 `existsByPair`와 동일한 native 판정 재사용.
- `@Component`, `JPAQueryFactory`(+필요 시 `EntityManager`) 주입.

- [ ] **Step 4: GetRegionProximityAdapter 작성**

`meeple-infra/.../region/GetRegionProximityAdapter.kt`:
```kotlin
package com.org.meeple.infra.region

import com.org.meeple.core.common.region.GetRegionProximityPort
import org.springframework.stereotype.Component

/**
 * [GetRegionProximityPort]의 인프라 구현. core 포트를 infra 근접 스냅샷([RegionProximityRegistry])에 잇는다.
 * (온보딩·배치와 같은 스냅샷을 공유한다)
 */
@Component
class GetRegionProximityAdapter(
	private val regionProximityRegistry: RegionProximityRegistry,
) : GetRegionProximityPort {

	override fun nearbyRegionIds(regionId: Long): List<Long> =
		regionProximityRegistry.nearbyRegionIds(regionId)
}
```

- [ ] **Step 5: 컴파일**

Run: `./gradlew :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra
git commit -m "feat(match): 추가 소개 후보 조회·지역 근접 인프라 어댑터 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: api 컨트롤러 + 응답 DTO

**Files:**
- Create: `meeple-api/.../match/response/ExtraIntroCandidatesResponse.kt`
- Create: `meeple-api/.../match/response/ExtraIntroResponse.kt`
- Modify: `meeple-api/.../match/SoloMatchController.kt` (엔드포인트 2개 추가)

**Interfaces:**
- Consumes: `GetExtraIntroCandidatesUseCase.getCandidates(userId)`, `IntroduceExtraMatchUseCase.introduce(userId)`, `ApiResponse`, `@LoginUser AuthUser`.
- Produces: `GET /matches/v1/extra/candidates` → `ApiResponse<ExtraIntroCandidatesResponse>`; `POST /matches/v1/extra` → `ApiResponse<ExtraIntroResponse>`.

- [ ] **Step 1: 응답 DTO 작성**

`ExtraIntroResponse.kt`:
```kotlin
package com.org.meeple.api.match.response

import com.org.meeple.core.solomatch.command.domain.Match

/** 추가 소개 결과. 생성된 매칭 id와 상대 userId. (상대 프로필은 매칭 목록 조회로 표시) */
data class ExtraIntroResponse(
	val matchId: Long,
	val partnerUserId: Long,
) {
	companion object {
		fun of(match: Match, requesterId: Long): ExtraIntroResponse =
			ExtraIntroResponse(matchId = match.id, partnerUserId = match.partnerOf(requesterId))
	}
}
```

`ExtraIntroCandidatesResponse.kt` — `ExtraIntroCandidate`(core read model) → 응답 DTO 매핑. 프론트 표시 필드에 맞춰 nickname·job·companyName·나이(birthday→age)·activityArea·traits·interests·height·bodyType·maritalStatus·smokingStatus·drinkingStatus·religion·profileImageCode·gender 포함. `PartnerResponse`/`MatchResponse`의 나이 계산·필드 매핑 패턴을 참고한다.
```kotlin
package com.org.meeple.api.match.response

import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroCandidate
import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroCandidates
import java.time.LocalDate

/** 추가 소개 후보 목록 응답. [totalCount]=전체 자격 후보 수, [candidates]=점수 상위 표시 목록. */
data class ExtraIntroCandidatesResponse(
	val totalCount: Int,
	val candidates: List<ExtraIntroCandidateResponse>,
) {
	companion object {
		fun of(result: ExtraIntroCandidates, today: LocalDate): ExtraIntroCandidatesResponse =
			ExtraIntroCandidatesResponse(
				totalCount = result.totalCount,
				candidates = result.candidates.map { c: ExtraIntroCandidate -> ExtraIntroCandidateResponse.of(c, today) },
			)
	}
}

data class ExtraIntroCandidateResponse(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val age: Int?,
	val height: Int?,
	val gender: String?,
	val job: String?,
	val activityArea: String?,
	val introduction: String?,
	val companyName: String?,
	val universityName: String?,
	val traits: List<String>,
	val interests: List<String>,
	val maritalStatus: String?,
	val smokingStatus: String?,
	val religion: String?,
	val drinkingStatus: String?,
	val bodyType: String?,
) {
	companion object {
		fun of(c: ExtraIntroCandidate, today: LocalDate): ExtraIntroCandidateResponse =
			ExtraIntroCandidateResponse(
				userId = c.userId,
				nickname = c.nickname,
				profileImageCode = c.profileImageCode,
				age = c.birthday?.let { b: LocalDate -> today.year - b.year - (if (today.dayOfYear < b.dayOfYear) 1 else 0) },
				height = c.height,
				gender = c.gender?.name,
				job = c.job,
				activityArea = c.activityArea,
				introduction = c.introduction,
				companyName = c.companyName,
				universityName = c.universityName,
				traits = c.traits,
				interests = c.interests,
				maritalStatus = c.maritalStatus?.name,
				smokingStatus = c.smokingStatus?.name,
				religion = c.religion?.name,
				drinkingStatus = c.drinkingStatus?.name,
				bodyType = c.bodyType?.name,
			)
	}
}
```
> 나이 계산은 기존 `MatchResponse`/`PartnerResponse`가 쓰는 방식이 있으면 그 헬퍼를 재사용한다(중복 구현 금지). Step 실행 시 해당 파일을 먼저 읽어 통일한다.

- [ ] **Step 2: 컨트롤러 엔드포인트 추가**

`SoloMatchController`에 두 UseCase 주입 추가 + 메서드 2개:
```kotlin
	@Operation(summary = "추가 소개 후보 조회", description = "오늘의 추천 외 추가로 소개받을 수 있는 자격 후보 상위 목록과 전체 후보 수를 반환한다.")
	@GetMapping("/extra/candidates")
	fun extraIntroCandidates(
		@LoginUser user: AuthUser,
	): ApiResponse<ExtraIntroCandidatesResponse> =
		ApiResponse.success(ExtraIntroCandidatesResponse.of(getExtraIntroCandidatesUseCase.getCandidates(user.id), timeGenerator.today()))

	@Operation(summary = "추가 소개 받기", description = "코인을 차감하고 자격 후보 1명을 골라 매칭을 생성한다. 후보가 없으면 실패한다.")
	@PostMapping("/extra")
	fun introduceExtra(
		@LoginUser user: AuthUser,
	): ApiResponse<ExtraIntroResponse> =
		ApiResponse.success(ExtraIntroResponse.of(introduceExtraMatchUseCase.introduce(user.id), user.id))
```
생성자에 추가:
```kotlin
	private val getExtraIntroCandidatesUseCase: GetExtraIntroCandidatesUseCase,
	private val introduceExtraMatchUseCase: IntroduceExtraMatchUseCase,
```
import 추가: `GetExtraIntroCandidatesUseCase`, `IntroduceExtraMatchUseCase`, 두 응답 DTO, `GetMapping`/`PostMapping`은 기존 존재.

- [ ] **Step 3: 컴파일**

Run: `./gradlew :meeple-api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/api/match
git commit -m "feat(match): 추가 소개 조회·생성 API 엔드포인트 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: E2E 테스트

**Files:**
- Create: `meeple-api/src/test/kotlin/com/org/meeple/api/match/ExtraIntroCandidatesIntegrationTest.kt`
- Create: `meeple-api/src/test/kotlin/com/org/meeple/api/match/ExtraIntroIntegrationTest.kt`

**Interfaces:**
- Consumes: `AbstractIntegrationSupport`, `IntegrationUtil`/엔티티 픽스처(infra testFixtures), `RestAssuredDsl`. (리포지토리 직접 의존 금지)

- [ ] **Step 1: 기존 E2E 패턴 확인**

`GetMatchCandidateIntegrationTest.kt` 및 기타 `*IntegrationTest`를 읽어 픽스처 생성(유저·user_details·user_ideal_types·match_user·region·코인 잔액), 로그인 토큰, RestAssured 호출/검증 패턴을 파악한다.

- [ ] **Step 2: 조회 E2E 작성**

`ExtraIntroCandidatesIntegrationTest.kt` — 시나리오:
- 반대 성별 자격 후보 12명+ 생성 → `GET /matches/v1/extra/candidates` → `candidates.size == 11`, `totalCount == 실제 자격 후보 수`.
- 같은 성별/최근 로그인 아님/이미 소개된 상대는 후보에서 제외되는지.
- 매칭 불가(match_user 없음) 요청자는 `totalCount == 0`, 빈 목록.

- [ ] **Step 3: 조회 E2E 실행**

Run: `./gradlew :meeple-api:test --tests "*ExtraIntroCandidatesIntegrationTest"`
Expected: PASS

- [ ] **Step 4: 추가 소개 E2E 작성**

`ExtraIntroIntegrationTest.kt` — 시나리오:
- 코인 충분 + 자격 후보 존재 → `POST /matches/v1/extra` → 200, `matchId`/`partnerUserId` 존재. 코인 잔액 30 감소. 생성된 매칭이 `GET /matches/v1`에 노출되고 이후 `POST /matches/v1/{id}/interest`로 흐름 진입 가능.
- 자격 후보 없음 → 실패(EXTRA_INTRO_NO_CANDIDATE) + 코인 잔액 불변.
- 코인 부족 → 실패 + 매칭 미생성.
- 재소개 제외: 이미 소개된 상대만 있으면 후보 없음 처리.

- [ ] **Step 5: 추가 소개 E2E 실행**

Run: `./gradlew :meeple-api:test --tests "*ExtraIntroIntegrationTest"`
Expected: PASS

- [ ] **Step 6: 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (기존 테스트 포함 전부 통과)

- [ ] **Step 7: 커밋**

```bash
git add meeple-api/src/test/kotlin/com/org/meeple/api/match
git commit -m "test(match): 추가 소개 조회·생성 E2E 시나리오 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **스펙 커버리지**: (1)모듈 분리=Task1·2, (2)조회 API=Task4·6·7·8, (3)추가소개 API=Task5·6·7·8, (4)enum/에러/락=Task3, (5)테스트=Task2·8, (6)프론트 안내=스펙 문서 §6(코드 변경 없음). 누락 없음.
- **플레이스홀더**: 알고리즘/서비스/enum/컨트롤러는 완전 코드 제공. infra QueryDSL·E2E는 "기존 파일 패턴을 먼저 읽고 동일하게 구현"으로 위임(실제 엔티티 필드명이 코드베이스 확인 필요 사항이라, 참고 파일을 명시). 실행 시 해당 파일 확인 필수.
- **타입 일관성**: `ScoringCandidate`(userId/regionId/lastLoginAt), `MatchScoringProfile`, `MatchSelector.selectBest/orderByScore` 시그니처가 Task2 정의와 Task4·5 사용에서 일치. `ExtraIntroScoringRow`(query)/`ExtraIntroCandidateRow`(command) 분리는 CQRS 의도.
- **주의**: infra Task6은 실제 엔티티 필드·memberKey 재소개 판정을 코드에서 확인해야 하므로, 실행 서브에이전트는 Step1의 참고 파일을 반드시 먼저 읽을 것.
