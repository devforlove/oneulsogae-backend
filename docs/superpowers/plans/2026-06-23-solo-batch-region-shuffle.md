# 일일 솔로 배치 근접 지역 셔플(다양성) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 일일 솔로 매칭 배치가 가장 가까운 10개 지역의 순회 순서를 무작위로 섞어, 같은 근접 후보만 반복 선택되지 않고 매칭 다양성을 갖게 한다.

**Architecture:** 무작위성을 `RegionShuffler` out-port로 격리(`TimeGenerator` 선례)해 결정적 테스트를 가능케 한다. 운영 구현 `RandomRegionShuffler`는 가까운 순 regionId 목록의 앞 10개만 섞고 그 이후는 순서를 유지한다. `SoloMatchBatchService`는 순회 직전 이 포트로 지역 순서를 받는다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / `kotlin.random.Random` / Kotest / Testcontainers(MySQL).

## Global Constraints

- 범위는 **셔플만**. `existsByPair`(재소개 이력 스킵) 호출·지역 내 후보 순서(lastLogin desc)는 **변경하지 않는다**(효율 개선=배치화는 범위 밖).
- 셔플 단위: 가까운 순 regionId 목록의 **앞 K=10개만** 무작위로 섞고, K번째 이후는 순서 유지. 입력이 K개 이하면 전체가 셔플 대상, 1개 이하면 그대로.
- 무작위성은 `RegionShuffler` out-port로 격리한다(`TimeGenerator`/`SystemBatchTimeGenerator`와 같은 패키지·관례). 운영 구현은 scheduler가 직접 제공(프레임워크 외 인프라 비의존).
- 타입 명시(변수·반환·람다 파라미터). `MatchStatus`/도메인 무관.
- 작업은 `main`이 아닌 작업 브랜치에서. 시작 전 `git checkout -b feat/solo-batch-region-shuffle`.
- 시작 시 작업 트리에 미커밋된 `SoloMatchBatchService`의 `// TODO 개선` 한 줄이 있다(이 기능이 그 해소). Task 2에서 제거된다.

---

### Task 1: `RegionShuffler` 포트 + `RandomRegionShuffler` 구현 + 유닛 테스트

무작위성 seam과 운영 구현을 추가한다. 아직 서비스에 연결하지 않는다(독립 컴파일·유닛 검증).

**Files:**
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/RegionShuffler.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/RandomRegionShuffler.kt`
- Create (test): `meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/RandomRegionShufflerTest.kt`

**Interfaces:**
- Produces: `RegionShuffler.shuffleNearest(regionIds: List<Long>): List<Long>` (fun interface); `RandomRegionShuffler(random: Random = Random.Default)` (`@Component`, 앞 10개 셔플).

- [ ] **Step 1: 유닛 테스트 작성 (구조 불변식)**

`meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/RandomRegionShufflerTest.kt`:
```kotlin
package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.command.application.port.out.RandomRegionShuffler
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/**
 * [RandomRegionShuffler] 유닛 테스트.
 * "앞 10개만 섞고 11번째 이후는 순서 유지"를 구조 불변식으로 검증한다. (시드 고정으로 결정적)
 */
class RandomRegionShufflerTest : DescribeSpec({

	describe("shuffleNearest") {

		it("앞 10개는 같은 원소의 재배열, 11번째 이후는 순서를 유지한다") {
			val input: List<Long> = (1L..15L).toList()
			val shuffler = RandomRegionShuffler(Random(42))

			val result: List<Long> = shuffler.shuffleNearest(input)

			result.size shouldBe 15
			result.sorted() shouldBe input                 // 원소 보존(누락·중복 없음)
			result.drop(10) shouldBe input.drop(10)        // 11~15는 가까운 순 그대로
			result.take(10).sorted() shouldBe (1L..10L).toList() // 앞 10개는 자기들끼리의 순열
		}

		it("입력이 10개 이하이면 전체가 셔플 대상이고 원소를 보존한다") {
			val input: List<Long> = (1L..5L).toList()
			val shuffler = RandomRegionShuffler(Random(1))

			val result: List<Long> = shuffler.shuffleNearest(input)

			result.sorted() shouldBe input
		}

		it("입력이 1개 이하이면 그대로 반환한다") {
			val shuffler = RandomRegionShuffler(Random(1))

			shuffler.shuffleNearest(emptyList()) shouldBe emptyList()
			shuffler.shuffleNearest(listOf(7L)) shouldBe listOf(7L)
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인** (구현 전이라 컴파일/실행 실패)

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.RandomRegionShufflerTest"`
Expected: FAIL (RandomRegionShuffler 미존재로 컴파일 에러)

- [ ] **Step 3: 포트 작성**

`RegionShuffler.kt`:
```kotlin
package com.org.meeple.scheduler.match.command.application.port.out

/**
 * 가까운 순 regionId 목록의 순회 순서를 무작위로 흔들어 매칭 다양성을 주는 아웃포트.
 * 배치 로직이 무작위성을 직접 다루지 않고 이 인터페이스에 의존하게 해, 테스트에서 결정적으로 만들 수 있다.
 * (시각의 [TimeGenerator]와 같은 격리 의도. 구현은 scheduler가 직접 제공한다)
 */
fun interface RegionShuffler {

	/** 가까운 순 regionId 목록에서 앞 K개만 무작위로 섞은 새 목록을 반환한다. (K번째 이후는 순서 유지) */
	fun shuffleNearest(regionIds: List<Long>): List<Long>
}
```

- [ ] **Step 4: 운영 구현 작성**

`RandomRegionShuffler.kt`:
```kotlin
package com.org.meeple.scheduler.match.command.application.port.out

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 가까운 순 regionId 목록의 앞 [NEAREST_SHUFFLE_COUNT]개만 무작위로 섞는 [RegionShuffler] 기본 구현.
 * 매칭 상대를 "가장 가까운 N개 지역 안"으로 한정하되 그중 순서를 흔들어, 항상 같은 최근접 후보만 뽑히지 않게 한다.
 * (인프라 의존이 없어 scheduler 모듈에서 직접 제공한다. [random]은 테스트에서 시드 고정용으로 주입한다)
 */
@Component
class RandomRegionShuffler(
	private val random: Random = Random.Default,
) : RegionShuffler {

	override fun shuffleNearest(regionIds: List<Long>): List<Long> {
		if (regionIds.size <= 1) return regionIds
		val head: List<Long> = regionIds.take(NEAREST_SHUFFLE_COUNT).shuffled(random)
		val tail: List<Long> = regionIds.drop(NEAREST_SHUFFLE_COUNT)
		return head + tail
	}

	companion object {
		/** 순서를 섞을 최근접 지역 개수. */
		private const val NEAREST_SHUFFLE_COUNT = 10
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.RandomRegionShufflerTest"`
Expected: PASS (3 케이스)

- [ ] **Step 6: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/RegionShuffler.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/RandomRegionShuffler.kt \
        meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/RandomRegionShufflerTest.kt
git commit -m "feat(match): 근접 지역 순서 셔플 RegionShuffler 포트·구현 추가"
```

---

### Task 2: `SoloMatchBatchService`에 셔플 연결 + 통합 테스트 결정성

서비스가 순회 직전 `RegionShuffler`로 지역 순서를 받게 하고, 기존 통합 테스트는 항등 셔플러로 결정성을 유지한다.

**Files:**
- Modify: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/SoloMatchBatchService.kt`
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/scheduler/RunSoloMatchBatchIntegrationTest.kt`

**Interfaces:**
- Consumes: `RegionShuffler.shuffleNearest(...)` (Task 1).

- [ ] **Step 1: `SoloMatchBatchService`에 `RegionShuffler` 주입 + 순회 연결 + TODO 제거**

import 추가: `import com.org.meeple.scheduler.match.command.application.port.out.RegionShuffler`

생성자(현재 28~34행)에 파라미터 추가 — `timeGenerator` 다음에:
```kotlin
	private val timeGenerator: TimeGenerator,
	private val regionShuffler: RegionShuffler,
) : RunSoloMatchBatchUseCase {
```

`findNearestFreshPartner`(현재 80~90행)를 아래로 교체 (`// TODO 개선` 제거 + 셔플 적용):
```kotlin
	/** [target] 지역에서 가까운 순 상위 N개 지역을 무작위 순서로 뒤져, 재소개 이력이 없는 첫 후보를 찾는다. (없으면 null) */
	private fun findNearestFreshPartner(target: MatchableUser, pool: MatchPool): MatchableUser? {
		val partnerGender: Gender = target.gender.opposite()
		val regionOrder: List<Long> = regionShuffler.shuffleNearest(regionProximityPort.nearbyRegionIds(target.regionId))
		for (regionId: Long in regionOrder) {
			for (candidate: MatchableUser in pool.freshCandidates(partnerGender, regionId)) {
				if (!getMatchRecordDao.existsByPair(target.userId, candidate.userId)) return candidate
			}
		}
		return null
	}
```

- [ ] **Step 2: 통합 테스트에 항등 `RegionShuffler` 주입 (결정성 유지)**

`RunSoloMatchBatchIntegrationTest.kt`:
- import 추가:
  ```kotlin
  import com.org.meeple.scheduler.match.command.application.port.out.RegionShuffler
  import org.springframework.boot.test.context.TestConfiguration
  import org.springframework.context.annotation.Bean
  import org.springframework.context.annotation.Import
  import org.springframework.context.annotation.Primary
  ```
- 클래스 선언에 `@Import` 추가하고, 클래스 본문(닫는 `})` 뒤)에 항등 셔플러 `@TestConfiguration`을 둔다. 현재:
  ```kotlin
  class RunSoloMatchBatchIntegrationTest(
  	private val runSoloMatchBatchUseCase: RunSoloMatchBatchUseCase,
  ) : AbstractIntegrationSupport({
  ```
  를
  ```kotlin
  @Import(RunSoloMatchBatchIntegrationTest.DeterministicShufflerConfig::class)
  class RunSoloMatchBatchIntegrationTest(
  	private val runSoloMatchBatchUseCase: RunSoloMatchBatchUseCase,
  ) : AbstractIntegrationSupport({
  ```
  로 바꾸고, 파일에서 스펙 람다를 닫는 `})` **직후**(현재 151행 `})` 다음, 최상위 private 헬퍼들 **앞**)에 추가:
  ```kotlin
  }) {

  	/**
  	 * 통합 테스트는 셔플을 항등(순서 유지)으로 고정해 결정적으로 만든다.
  	 * 실제 셔플 로직은 RandomRegionShufflerTest(유닛)에서 검증한다.
  	 */
  	@TestConfiguration
  	class DeterministicShufflerConfig {
  		@Bean
  		@Primary
  		fun deterministicRegionShuffler(): RegionShuffler = RegionShuffler { regionIds: List<Long> -> regionIds }
  	}
  ```
  (기존 최상위 private 헬퍼 `persistRegion`/`persistMatchableUser`/… 는 그대로 둔다. 위 추가는 클래스 본문을 여는 것이므로, 파일 끝 구조가 `… }) { @TestConfiguration … } \n\n private fun persistRegion(…)` 형태가 되도록 클래스 닫는 `}`를 스펙 람다 직후에 연 `{` 와 짝맞춰 둔다.)

  주의: Kotest 생성자-람다 스타일에서 클래스 본문을 추가하므로, 스펙 람다를 닫는 `})` 를 `}) {` 로 바꾸고, `DeterministicShufflerConfig` 정의 뒤에 클래스를 닫는 `}` 를 넣은 다음, 그 아래에 기존 최상위 `private fun …` 들이 오게 한다.

- [ ] **Step 3: 통합 테스트 실행 (결정성 + 기존 단언 유지)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.scheduler.RunSoloMatchBatchIntegrationTest"`
Expected: PASS (8 케이스 모두 — 항등 셔플러라 "가까운 지역 후보와 소개"가 결정적으로 통과)

- [ ] **Step 4: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (실패 시: `SoloMatchBatchService`를 수동 생성하는 다른 곳이 없는지 grep `SoloMatchBatchService(` — 현재는 Spring DI만 사용. `@Primary` 빈이 둘 이상이면 충돌 메시지 확인)

- [ ] **Step 5: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/SoloMatchBatchService.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/scheduler/RunSoloMatchBatchIntegrationTest.kt
git commit -m "feat(match): 일일 배치가 가까운 10개 지역을 셔플해 매칭 다양성 부여"
```

---

## 완료 기준 (Definition of Done)

- `./gradlew build` 통과.
- `SoloMatchBatchService.findNearestFreshPartner`가 `regionShuffler.shuffleNearest(...)` 순서로 순회. `// TODO 개선` 제거됨.
- `RandomRegionShuffler`: 앞 10개만 셔플(원소 보존)·11번째 이후 순서 유지·1개 이하 그대로 — 유닛 테스트로 검증.
- `existsByPair`·지역 내 후보 순서 불변(범위 밖 항목 미변경).
- 통합 테스트는 항등 셔플러로 결정적이며 기존 8케이스 GREEN 유지.
