# 거리 기반 1:1 매칭 재작성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 1:1 매칭(일일 배치 + 온보딩 추천)의 후보 선택을 "같은 regionCode + 랜덤"에서 "가까운 region 순서(지리적 거리)"로 바꾸고, 한 번이라도 소개된 쌍은 재소개하지 않는다.

**Architecture:** 지역 좌표(`RegionEntity` 위경도)로 "각 지역 → 가까운 지역 순서"를 계산하는 순수 `RegionProximity`를 신설한다. 배치는 Redis 풀 키를 `regionId`로 바꾸고 근접 지역 순서대로 풀을 순회하며, 온보딩은 근접 지역을 순회하며 이력 없는 최근접 후보를 DB로 조회한다. 팀(2:2)이 쓰는 `regionCode`는 손대지 않는다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA + QueryDSL / Redis(StringRedisTemplate) / Kotest(DescribeSpec) / Testcontainers(MySQL·Redis).

## Global Constraints

- 모든 변수·반환 타입·람다 파라미터 타입을 **명시**한다(표현식 본문 함수 포함).
- 현재 시각은 `LocalDateTime.now()` 직접 호출 금지. 애플리케이션/도메인은 `TimeGenerator.now()`를 쓰고 도메인엔 파라미터로 넘긴다. (이번 변경 코드에 신규 `now()` 직접 호출을 넣지 않는다)
- **CQS/CQRS 유지**: 조회는 부수효과 없이, 명령은 도메인 모델로. 헥사고날 경계 유지(core는 인프라 세부에 비의존, scheduler는 core에 비의존 — 자기 포트/ dao만 보유).
- `@Query`(JPQL) 조인은 콤마 암묵 조인 금지, `join … on` 명시 조인.
- **`regionCode`(시도 기반 1~5)는 절대 제거하지 않는다.** `match_user.region_code`, `UserDetail.regionCode`, `MatchUser.regionCode`, `MatchProfileSnapshot.regionCode`, `Region.resolveAreaCode`, 팀 DAO들은 그대로 둔다. 1:1 경로만 `regionId`로 바꾼다.
- 변경된 레이어의 테스트를 함께 갱신한다(순수 로직→Kotest 유닛, api 경계→통합/E2E).
- 본 작업은 `main`이 아닌 작업 브랜치에서 수행한다. 시작 전 `git checkout -b feat/distance-based-matching`(현재 `feat/recommended-team`에서 분기).

---

### Task 1: `RegionProximity` 순수 계산기

지역 좌표 목록으로부터 "각 지역에서 가까운 지역 순서"를 계산하는 순수 클래스. 프레임워크/인프라 비의존 → Kotest 유닛으로 검증.

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/region/RegionProximity.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/region/RegionProximityTest.kt`

**Interfaces:**
- Produces:
  - `data class RegionPoint(val regionId: Long, val latitude: Double, val longitude: Double)`
  - `class RegionProximity` with `fun nearbyRegionIds(regionId: Long): List<Long>`, `companion object { val EMPTY: RegionProximity; fun from(points: List<RegionPoint>): RegionProximity }`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.org.meeple.region

import com.org.meeple.infra.region.RegionPoint
import com.org.meeple.infra.region.RegionProximity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class RegionProximityTest : DescribeSpec({

	// 같은 경도에서 위도만 차이 — 위도 0.1도 ≈ 11km, 1.0도 ≈ 111km
	val seoul = RegionPoint(regionId = 1L, latitude = 37.0, longitude = 127.0)
	val near = RegionPoint(regionId = 2L, latitude = 37.1, longitude = 127.0)
	val far = RegionPoint(regionId = 3L, latitude = 38.0, longitude = 127.0)

	describe("nearbyRegionIds") {

		context("기준 지역이 포함돼 있으면") {
			it("자기 지역이 거리 0으로 맨 앞이고, 나머지는 가까운 순으로 정렬된다") {
				val proximity: RegionProximity = RegionProximity.from(listOf(far, seoul, near))

				proximity.nearbyRegionIds(1L) shouldBe listOf(1L, 2L, 3L)
			}
		}

		context("거리가 같은 지역이 둘이면") {
			it("regionId 오름차순으로 안정 정렬한다") {
				val east = RegionPoint(regionId = 5L, latitude = 37.0, longitude = 127.1)
				val west = RegionPoint(regionId = 4L, latitude = 37.0, longitude = 126.9)
				val proximity: RegionProximity = RegionProximity.from(listOf(seoul, east, west))

				// east/west는 seoul에서 거의 같은 거리 → regionId 작은 4가 먼저
				proximity.nearbyRegionIds(1L) shouldBe listOf(1L, 4L, 5L)
			}
		}

		context("모르는 지역이면") {
			it("빈 리스트를 반환한다") {
				val proximity: RegionProximity = RegionProximity.from(listOf(seoul, near))

				proximity.nearbyRegionIds(999L).shouldBeEmpty()
			}
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.region.RegionProximityTest"`
Expected: FAIL — `RegionProximity`/`RegionPoint` 미존재 컴파일 에러.

- [ ] **Step 3: 구현 작성**

```kotlin
package com.org.meeple.infra.region

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 지역 대표 좌표 한 점. (거리 계산 입력) */
data class RegionPoint(
	val regionId: Long,
	val latitude: Double,
	val longitude: Double,
)

/**
 * 지역 좌표로부터 "각 지역에서 가까운 지역 순서"를 계산하는 순수 도메인 로직.
 * 유저는 개인 좌표가 없고 regionId만 가지므로, 매칭의 "가까움"은 지역 대표 좌표 간 거리로 수렴한다.
 * 한 번의 조회는 한 지역의 정렬만 필요하므로(O(n log n)), 전체 쌍을 미리 펼치지 않고 호출 시 정렬한다.
 * 프레임워크/인프라에 의존하지 않는다. (캐싱·DB 적재는 [RegionProximityRegistry]가 맡는다)
 */
class RegionProximity private constructor(
	private val points: List<RegionPoint>,
) {

	/**
	 * [regionId]에서 가까운 순으로 정렬한 전체 regionId. 자기 지역이 거리 0으로 맨 앞에 온다.
	 * 거리가 같으면 regionId 오름차순으로 안정 정렬한다. 좌표를 모르는 지역이면 빈 리스트.
	 */
	fun nearbyRegionIds(regionId: Long): List<Long> {
		val origin: RegionPoint = points.firstOrNull { point: RegionPoint -> point.regionId == regionId }
			?: return emptyList()
		return points
			.sortedWith(
				compareBy(
					{ point: RegionPoint -> haversineKm(origin, point) },
					{ point: RegionPoint -> point.regionId },
				),
			)
			.map { point: RegionPoint -> point.regionId }
	}

	companion object {
		/** 좌표가 하나도 없는 빈 근접표. (적재 전 초기 상태) */
		val EMPTY: RegionProximity = RegionProximity(emptyList())

		fun from(points: List<RegionPoint>): RegionProximity =
			RegionProximity(points)

		private const val EARTH_RADIUS_KM: Double = 6371.0

		/** 두 좌표 간 대권 거리(km). 정렬용이라 절대값 정확도보다 단조성만 보장하면 충분하다. */
		private fun haversineKm(a: RegionPoint, b: RegionPoint): Double {
			val dLat: Double = Math.toRadians(b.latitude - a.latitude)
			val dLon: Double = Math.toRadians(b.longitude - a.longitude)
			val lat1: Double = Math.toRadians(a.latitude)
			val lat2: Double = Math.toRadians(b.latitude)
			val h: Double = sin(dLat / 2) * sin(dLat / 2) +
				cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
			return EARTH_RADIUS_KM * 2 * atan2(sqrt(h), sqrt(1 - h))
		}
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.region.RegionProximityTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/region/RegionProximity.kt \
        meeple-api/src/test/kotlin/com/org/meeple/region/RegionProximityTest.kt
git commit -m "feat(match): 지역 근접 순서 계산 RegionProximity 추가"
```

---

### Task 2: `RegionProximityRegistry` (인프라 캐시)

`RegionEntity` 좌표를 읽어 `RegionProximity` 스냅샷을 만들어 메모리에 캐시한다. 두 경로(온보딩 어댑터·배치 포트 어댑터)가 공유한다.

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/region/RegionProximityRegistry.kt`

**Interfaces:**
- Consumes: `RegionProximity.from(...)` (Task 1), `RegionJpaRepository.findAll()` (기존).
- Produces: `class RegionProximityRegistry` with `fun refresh()`, `fun nearbyRegionIds(regionId: Long): List<Long>`.

- [ ] **Step 1: 구현 작성** (얇은 캐시 어댑터 — 단독 유닛 테스트 없이 Task 10/11 통합으로 커버)

```kotlin
package com.org.meeple.infra.region

import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.infra.region.repository.RegionJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * [RegionProximity] 스냅샷을 메모리에 캐시하는 인프라 컴포넌트.
 * regions는 정적 참조 데이터라 기동 시 한 번 적재([warmUp])하고, 일일 배치 시작 등에서 [refresh]로 갱신한다.
 * 두 매칭 경로(온보딩 후보 어댑터·배치 근접 포트 어댑터)가 같은 스냅샷을 공유한다.
 */
@Component
class RegionProximityRegistry(
	private val regionJpaRepository: RegionJpaRepository,
) {

	@Volatile
	private var proximity: RegionProximity = RegionProximity.EMPTY

	/** 기동 시 한 번 적재한다. (regions가 비어 있어도 EMPTY로 안전) */
	@PostConstruct
	fun warmUp() {
		refresh()
	}

	/** regions 좌표를 다시 읽어 근접 스냅샷을 교체한다. (참조 데이터 변경 반영) */
	fun refresh() {
		val points: List<RegionPoint> = regionJpaRepository.findAll()
			.map { region: RegionEntity ->
				RegionPoint(
					regionId = region.id!!,
					latitude = region.latitude,
					longitude = region.longitude,
				)
			}
		proximity = RegionProximity.from(points)
	}

	/** [regionId]에서 가까운 순으로 정렬한 전체 regionId. (스냅샷 위임) */
	fun nearbyRegionIds(regionId: Long): List<Long> =
		proximity.nearbyRegionIds(regionId)
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/region/RegionProximityRegistry.kt
git commit -m "feat(match): 지역 근접 스냅샷 캐시 RegionProximityRegistry 추가"
```

---

### Task 3: scheduler `RegionProximityPort` + 인프라 어댑터

배치(scheduler)가 core/infra에 직접 의존하지 않고 근접 지역 순서를 얻기 위한 out-port와 그 인프라 구현.

**Files:**
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/RegionProximityPort.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/RegionProximityAdapter.kt`

**Interfaces:**
- Consumes: `RegionProximityRegistry` (Task 2).
- Produces: `interface RegionProximityPort { fun refresh(); fun nearbyRegionIds(regionId: Long): List<Long> }`

- [ ] **Step 1: 포트 작성**

```kotlin
package com.org.meeple.scheduler.match.command.application.port.out

/**
 * 배치가 지역 근접 순서를 얻기 위한 아웃포트. (scheduler는 core·infra에 의존하지 않으므로 자기 포트만 정의)
 * 구현은 infra 레이어가 [com.org.meeple.infra.region.RegionProximityRegistry] 위임으로 담당한다.
 */
interface RegionProximityPort {

	/** 근접 스냅샷을 최신 regions로 갱신한다. (배치 시작 시 1회 호출) */
	fun refresh()

	/** [regionId]에서 가까운 순으로 정렬한 전체 regionId. 좌표를 모르는 지역이면 빈 리스트. */
	fun nearbyRegionIds(regionId: Long): List<Long>
}
```

- [ ] **Step 2: 어댑터 작성**

```kotlin
package com.org.meeple.infra.match.command.adapter

import com.org.meeple.infra.region.RegionProximityRegistry
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import org.springframework.stereotype.Component

/**
 * [RegionProximityPort]의 인프라 구현. scheduler 포트를 [RegionProximityRegistry](infra)로 잇는다.
 * (scheduler는 core/infra를 모르므로, regions 좌표를 아는 infra가 근접 계산을 제공한다)
 */
@Component
class RegionProximityAdapter(
	private val regionProximityRegistry: RegionProximityRegistry,
) : RegionProximityPort {

	override fun refresh() {
		regionProximityRegistry.refresh()
	}

	override fun nearbyRegionIds(regionId: Long): List<Long> =
		regionProximityRegistry.nearbyRegionIds(regionId)
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :meeple-scheduler:compileKotlin :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/RegionProximityPort.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/RegionProximityAdapter.kt
git commit -m "feat(match): 배치용 RegionProximityPort와 인프라 어댑터 추가"
```

---

### Task 4: Redis 풀 포트/어댑터/도메인 → `regionId`, 성별 폴백 제거

풀 키를 `regionCode`(Int)에서 `regionId`(Long)로 바꾸고, 더 이상 필요 없는 지역 무관 성별 폴백 풀을 제거한다.

**Files:**
- Modify: `meeple-scheduler/.../command/application/port/out/MatchPoolPort.kt`
- Modify: `meeple-scheduler/.../command/application/port/out/SaveMatchPoolPort.kt`
- Modify: `meeple-scheduler/.../command/domain/MatchPoolGroup.kt`
- Delete: `meeple-scheduler/.../command/domain/MatchPoolByGender.kt`
- Modify: `meeple-infra/.../match/command/adapter/MatchRedisAdapter.kt`

**Interfaces:**
- Produces (변경 후 시그니처):
  - `MatchPoolPort.pop(gender: Gender, regionId: Long): Long?`, `pushBack(gender: Gender, regionId: Long, userIds: List<Long>)`, `remove(gender: Gender, regionId: Long, userId: Long)`
  - `SaveMatchPoolPort.save(group: MatchPoolGroup)`
  - `MatchPoolGroup(gender: Gender, regionId: Long, userIds: List<Long>)`, `MatchPoolGroup.group(activeUsers): List<MatchPoolGroup>`, `shuffled(...)`

- [ ] **Step 1: `MatchPoolPort` 교체** — 성별 폴백 3개 메서드 삭제, `regionCode: Int` → `regionId: Long`

```kotlin
package com.org.meeple.scheduler.match.command.application.port.out

import com.org.meeple.common.user.Gender

/**
 * 매칭 후보 풀을 소비하는 아웃포트.
 * 풀 적재는 [SaveMatchPoolPort]가, 소비(꺼내기/되돌리기/제거)는 이 포트가 담당한다.
 * (성별, 지역=regionId) 풀 단위로 소비한다. 구현은 infra 레이어의 Redis 어댑터가 맡는다.
 */
interface MatchPoolPort {

	/**
	 * (성별, 지역) 풀에서 후보 한 명을 꺼낸다(pop). 꺼낸 사용자는 풀에서 제거된다.
	 * 풀이 비어 있으면 null. (Redis Set이라 어떤 한 명이 나오는지는 무작위 — 같은 지역 내 동순위 타이브레이크)
	 */
	fun pop(gender: Gender, regionId: Long): Long?

	/** [pop]했지만 매칭하지 못한(재소개 이력 충돌 등) 후보들을 풀에 되돌린다. */
	fun pushBack(gender: Gender, regionId: Long, userIds: List<Long>)

	/** 매칭이 성사된 사용자를 (성별, 지역) 풀에서 제거해, 같은 배치에서 다시 후보로 뽑히지 않게 한다. */
	fun remove(gender: Gender, regionId: Long, userId: Long)
}
```

- [ ] **Step 2: `SaveMatchPoolPort` 교체** — `saveByGender` 삭제

```kotlin
package com.org.meeple.scheduler.match.command.application.port.out

import com.org.meeple.scheduler.match.command.domain.MatchPoolGroup

/**
 * 매칭 후보 풀을 저장하는 아웃포트. (성별, 지역=regionId) 그룹 풀을 적재한다.
 * 구현은 infra 레이어의 Redis 어댑터가 담당한다.
 */
interface SaveMatchPoolPort {

	/** (성별, 지역) 그룹 풀을 저장한다. (match:pool:{gender}:{regionId}) */
	fun save(group: MatchPoolGroup)
}
```

- [ ] **Step 3: `MatchPoolGroup` 교체** — 키를 `regionId: Long`로

```kotlin
package com.org.meeple.scheduler.match.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.query.dto.ActiveUser
import kotlin.random.Random

/**
 * (성별, 지역=regionId)을 키로 묶은 매칭 후보 풀 그룹.
 * 매칭은 반대 성별·가까운 지역 순으로 이뤄지므로, 지역별로 풀을 적재해 두면 소개 시 근접 지역 풀을 차례로 참조할 수 있다.
 */
data class MatchPoolGroup(
	val gender: Gender,
	val regionId: Long,
	val userIds: List<Long>,
) {

	/**
	 * 후보 순서를 무작위로 섞은 새 그룹을 반환한다. (같은 지역 내 적재 순서 편향 제거)
	 * 무작위 소스([random])는 파라미터로 받아 도메인이 인프라/난수 구현에 직접 의존하지 않게 한다.
	 */
	fun shuffled(random: Random = Random.Default): MatchPoolGroup =
		copy(userIds = userIds.shuffled(random))

	companion object {

		/** 활성 사용자들을 (성별, 지역) 기준으로 묶어 그룹 목록으로 만든다. */
		fun group(activeUsers: List<ActiveUser>): List<MatchPoolGroup> =
			activeUsers
				.groupBy({ user: ActiveUser -> user.gender to user.regionId }, { user: ActiveUser -> user.userId })
				.map { (key: Pair<Gender, Long>, userIds: List<Long>) ->
					MatchPoolGroup(gender = key.first, regionId = key.second, userIds = userIds)
				}
	}
}
```

- [ ] **Step 4: `MatchPoolByGender` 삭제**

```bash
git rm meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/domain/MatchPoolByGender.kt
```

- [ ] **Step 5: `MatchRedisAdapter` 교체** — 키 `regionId`, 성별 메서드 제거

```kotlin
package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.out.MatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchPoolPort
import com.org.meeple.scheduler.match.command.domain.MatchPoolGroup
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * [SaveMatchPoolPort] · [MatchPoolPort]의 Redis 구현 어댑터.
 * userId들을 Redis Set으로 저장하고, 소개 배치가 근접 지역 순서대로 거기서 후보를 꺼내(pop) 소비한다.
 * 매 배치마다 기존 키를 지우고 새로 적재하며, TTL로 자동 만료시켜 다음 배치가 돌지 않아도 오래된 풀이 남지 않게 한다.
 * 키 형식: (성별,지역) 풀 match:pool:{gender}:{regionId}
 */
@Component
class MatchRedisAdapter(
	private val stringRedisTemplate: StringRedisTemplate,
) : SaveMatchPoolPort, MatchPoolPort {

	override fun save(group: MatchPoolGroup) {
		replaceSet(keyOf(group.gender, group.regionId), group.userIds)
	}

	// 기존 키를 지우고 userId들을 Set으로 새로 적재한 뒤 TTL을 건다. (비어 있으면 키를 지운 상태로 둔다)
	private fun replaceSet(key: String, userIds: List<Long>) {
		stringRedisTemplate.delete(key)
		if (userIds.isEmpty()) return

		val members: Array<String> = userIds.map { id: Long -> id.toString() }.toTypedArray()
		stringRedisTemplate.opsForSet().add(key, *members)
		stringRedisTemplate.expire(key, POOL_TTL)
	}

	// SPOP: Set에서 무작위 한 명을 꺼내며 동시에 제거한다. 비어 있으면 null.
	override fun pop(gender: Gender, regionId: Long): Long? =
		stringRedisTemplate.opsForSet().pop(keyOf(gender, regionId))?.toLong()

	// SADD: 되돌릴 후보들을 다시 넣는다. (마지막 멤버를 pop해 키가 사라졌다가 재생성될 수 있어 TTL을 다시 건다)
	override fun pushBack(gender: Gender, regionId: Long, userIds: List<Long>) {
		if (userIds.isEmpty()) return

		val key: String = keyOf(gender, regionId)
		val members: Array<String> = userIds.map { id: Long -> id.toString() }.toTypedArray()
		stringRedisTemplate.opsForSet().add(key, *members)
		stringRedisTemplate.expire(key, POOL_TTL)
	}

	// SREM: 매칭된 사용자를 풀에서 제거한다. (없는 멤버면 no-op)
	override fun remove(gender: Gender, regionId: Long, userId: Long) {
		stringRedisTemplate.opsForSet().remove(keyOf(gender, regionId), userId.toString())
	}

	private fun keyOf(gender: Gender, regionId: Long): String =
		"$KEY_PREFIX$gender:$regionId"

	companion object {
		private const val KEY_PREFIX: String = "match:pool:"

		/** 풀 만료 기간. 배치 주기(1일)보다 길게 잡아 다음 배치 전까지 풀이 유지되게 한다. */
		private val POOL_TTL: Duration = Duration.ofDays(2)
	}
}
```

- [ ] **Step 6: scheduler/infra 컴파일** (배치 서비스/도입자가 아직 옛 시그니처라 일부 실패 가능 — Task 6·7에서 정리)

Run: `./gradlew :meeple-scheduler:compileKotlin`
Expected: FAIL — `RunDailyMatchBatchService`/`MatchIntroducer`가 아직 옛 API 사용. (Task 6·7에서 해소; 지금은 본 파일들만 교체됨)

- [ ] **Step 7: 커밋** (Task 6·7과 한 흐름이라 컴파일 깨진 채 중간 커밋 — 다음 두 태스크와 연속 실행 전제)

```bash
git add -A meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/domain \
          meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out \
          meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/MatchRedisAdapter.kt
git commit -m "refactor(match): 매칭 풀 키를 regionId로 전환, 성별 폴백 풀 제거"
```

---

### Task 5: `ActiveUser`/`MatchBatchTarget` DTO + DAO 프로젝션 → `regionId`

배치가 소비하는 read model의 지역 키를 `regionCode`에서 `regionId`로 바꾸고, 프로젝션을 `region_id`로 돌린다.

**Files:**
- Modify: `meeple-scheduler/.../query/dto/ActiveUser.kt`
- Modify: `meeple-scheduler/.../query/dto/MatchBatchTarget.kt`
- Modify: `meeple-infra/.../user/query/GetActiveUserDaoImpl.kt`
- Modify: `meeple-infra/.../match/query/GetMatchBatchTargetDaoImpl.kt`

**Interfaces:**
- Produces: `ActiveUser(userId: Long, gender: Gender, regionId: Long)`, `MatchBatchTarget(..., regionId: Long? = null)`

- [ ] **Step 1: `ActiveUser` 교체**

```kotlin
package com.org.meeple.scheduler.match.query.dto

import com.org.meeple.common.user.Gender

/**
 * 매칭 풀 그룹핑 대상이 되는 활성 사용자 read model.
 * 성별·지역(regionId)이 모두 채워진 ACTIVE + 최근 로그인 사용자만 담는다. (그룹 키가 둘 다 필요하므로 non-null)
 */
data class ActiveUser(
	val userId: Long,
	val gender: Gender,
	val regionId: Long,
)
```

- [ ] **Step 2: `MatchBatchTarget` 교체** — `regionCode` → `regionId`

```kotlin
package com.org.meeple.scheduler.match.query.dto

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import java.time.LocalDateTime

/**
 * 매칭 배치 대상 한 건. 다음 커서 산출에 필요한 [lastLoginAt]과
 * 매칭 판단에 필요한 프로필([gender]/[maritalStatus]/[regionId])을 함께 담는다.
 */
data class MatchBatchTarget(
	val userId: Long,
	val lastLoginAt: LocalDateTime,
	val gender: Gender? = null,
	val maritalStatus: MaritalStatus? = null,
	val regionId: Long? = null,
)
```

- [ ] **Step 3: `GetActiveUserDaoImpl` 프로젝션 교체** — `detail.regionCode` → `detail.regionId`

`Projections.constructor(ActiveUser::class.java, user.id, detail.gender, detail.regionCode)` 의 마지막 인자를 `detail.regionId`로 바꾼다. (주석의 "regionCode" 표현도 "regionId"로) 결과:

```kotlin
			return queryFactory
				.select(
					Projections.constructor(
						ActiveUser::class.java,
						user.id,
						detail.gender,
						detail.regionId,
					),
				)
				.from(user)
				.join(detail).on(detail.userId.eq(user.id))
				.where(
					user.status.eq(UserStatus.ACTIVE),
					user.lastLoginAt.goe(loginAfter),
				)
				.orderBy(user.lastLoginAt.asc(), user.id.asc())
				.fetch()
```

- [ ] **Step 4: `GetMatchBatchTargetDaoImpl` 프로젝션 교체** — `matchUser.regionCode` → `matchUser.regionId`

`Projections.constructor(MatchBatchTarget::class.java, matchUser.userId, matchUser.lastLoginAt, matchUser.gender, matchUser.maritalStatus, matchUser.regionCode)` 의 마지막 인자를 `matchUser.regionId`로 바꾼다.

- [ ] **Step 5: 컴파일 확인** (배치 서비스는 Task 7에서 정리하므로 부분 실패 가능)

Run: `./gradlew :meeple-scheduler:compileKotlin`
Expected: FAIL (배치 서비스 미정리) — DTO/DAO 자체 에러는 없어야 함.

- [ ] **Step 6: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dto/ActiveUser.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dto/MatchBatchTarget.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/user/query/GetActiveUserDaoImpl.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetMatchBatchTargetDaoImpl.kt
git commit -m "refactor(match): 배치 read model 지역 키를 regionId로 전환"
```

---

### Task 6: `MatchIntroducer` 근접 지역 순회로 재작성

"같은 권역 → 성별 폴백" 2단계를, 근접 지역 순서대로 풀을 순회하며 첫 신선 후보를 고르는 방식으로 바꾼다.

**Files:**
- Modify: `meeple-scheduler/.../command/application/MatchIntroducer.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/MatchIntroducerTest.kt`

**Interfaces:**
- Consumes: `RegionProximityPort.nearbyRegionIds(regionId)` (Task 3), `MatchPoolPort.{pop,pushBack,remove}(gender, regionId, ...)` (Task 4).
- Produces: `MatchIntroducer.introduce(targetId: Long, gender: Gender, regionId: Long, regionByUserId: Map<Long, Long>, now: LocalDateTime): Long?`

- [ ] **Step 1: 실패하는 유닛 테스트 작성** (가짜 포트로 근접 우선·이력 스킵·소진 검증)

```kotlin
package com.org.meeple.scheduler.match

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.MatchIntroducer
import com.org.meeple.scheduler.match.command.application.port.out.MatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class MatchIntroducerTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 23, 0, 0)

	describe("introduce") {

		context("가까운 지역과 먼 지역에 모두 후보가 있으면") {
			it("가까운 지역 후보를 먼저 소개한다") {
				val pools = FakeMatchPoolPort(
					mutableMapOf(
						(Gender.FEMALE to 1L) to mutableListOf(100L), // 같은 지역
						(Gender.FEMALE to 2L) to mutableListOf(200L), // 먼 지역
					),
				)
				val records = FakeGetMatchRecordDao(existingPairs = emptySet())
				val saved = FakeSaveMatchRecordPort()
				val proximity = FakeRegionProximityPort(mapOf(1L to listOf(1L, 2L)))
				val introducer = MatchIntroducer(records, saved, pools, proximity)

				val partnerId: Long? = introducer.introduce(
					targetId = 10L, gender = Gender.MALE, regionId = 1L,
					regionByUserId = mapOf(100L to 1L), now = now,
				)

				partnerId shouldBe 100L
				saved.saved shouldBe listOf(Triple(10L, Gender.MALE, 100L))
			}
		}

		context("가까운 지역 후보가 모두 재소개 이력이면") {
			it("다음 가까운 지역에서 신선 후보를 찾는다") {
				val pools = FakeMatchPoolPort(
					mutableMapOf(
						(Gender.FEMALE to 1L) to mutableListOf(100L),
						(Gender.FEMALE to 2L) to mutableListOf(200L),
					),
				)
				// 남성 10L 과 100L 은 이미 소개됨 (정렬 키: "10-100")
				val records = FakeGetMatchRecordDao(existingPairs = setOf(10L to 100L))
				val saved = FakeSaveMatchRecordPort()
				val proximity = FakeRegionProximityPort(mapOf(1L to listOf(1L, 2L)))
				val introducer = MatchIntroducer(records, saved, pools, proximity)

				val partnerId: Long? = introducer.introduce(
					targetId = 10L, gender = Gender.MALE, regionId = 1L,
					regionByUserId = mapOf(200L to 2L), now = now,
				)

				partnerId shouldBe 200L
				// 이력 후보 100L 은 같은 지역 풀로 되돌아가 있다
				pools.peek(Gender.FEMALE, 1L) shouldBe listOf(100L)
			}
		}

		context("모든 근접 지역 풀이 비어 있으면") {
			it("소개하지 못하고 null을 반환한다") {
				val pools = FakeMatchPoolPort(mutableMapOf())
				val records = FakeGetMatchRecordDao(existingPairs = emptySet())
				val saved = FakeSaveMatchRecordPort()
				val proximity = FakeRegionProximityPort(mapOf(1L to listOf(1L, 2L)))
				val introducer = MatchIntroducer(records, saved, pools, proximity)

				introducer.introduce(
					targetId = 10L, gender = Gender.MALE, regionId = 1L,
					regionByUserId = emptyMap(), now = now,
				).shouldBeNull()
			}
		}
	}
})

private class FakeMatchPoolPort(
	private val pools: MutableMap<Pair<Gender, Long>, MutableList<Long>>,
) : MatchPoolPort {
	override fun pop(gender: Gender, regionId: Long): Long? =
		pools[gender to regionId]?.removeFirstOrNull()

	override fun pushBack(gender: Gender, regionId: Long, userIds: List<Long>) {
		if (userIds.isEmpty()) return
		pools.getOrPut(gender to regionId) { mutableListOf() }.addAll(userIds)
	}

	override fun remove(gender: Gender, regionId: Long, userId: Long) {
		pools[gender to regionId]?.remove(userId)
	}

	fun peek(gender: Gender, regionId: Long): List<Long> =
		pools[gender to regionId]?.toList() ?: emptyList()
}

private class FakeGetMatchRecordDao(
	private val existingPairs: Set<Pair<Long, Long>>,
) : GetMatchRecordDao {
	override fun existsByPair(userIdA: Long, userIdB: Long): Boolean {
		val key: Pair<Long, Long> = listOf(userIdA, userIdB).sorted().let { it[0] to it[1] }
		return existingPairs.any { listOf(it.first, it.second).sorted().let { s -> s[0] to s[1] } == key }
	}

	override fun findMatchedUserIds(): MatchedUserIds = MatchedUserIds(emptySet())
}

private class FakeSaveMatchRecordPort : SaveMatchRecordPort {
	val saved: MutableList<Triple<Long, Gender, Long>> = mutableListOf()
	override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
		saved.add(Triple(requesterId, requesterGender, partnerId))
	}
}

private class FakeRegionProximityPort(
	private val nearby: Map<Long, List<Long>>,
) : RegionProximityPort {
	override fun refresh() = Unit
	override fun nearbyRegionIds(regionId: Long): List<Long> = nearby[regionId] ?: emptyList()
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.MatchIntroducerTest"`
Expected: FAIL — `MatchIntroducer` 생성자/`introduce` 시그니처 불일치 컴파일 에러.

- [ ] **Step 3: `MatchIntroducer` 재작성**

```kotlin
package com.org.meeple.scheduler.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.out.MatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 한 대상 사용자에게 소개 상대를 찾아 소개를 생성하는 책임을 담당한다.
 * 대상 지역에서 **가까운 지역 순서**로 (반대 성별, 지역) Redis 풀을 순회하며, 재소개 이력이 없는 첫 후보를 상대로 정한다.
 * 같은 지역 풀 내에서는 Redis Set의 무작위 pop으로 동순위를 타이브레이크한다.
 * 일일 배치 순회 자체는 [RunDailyMatchBatchService]가 맡고, 사용자 한 명에 대한 소개 로직만 이 클래스로 분리한다.
 */
@Component
class MatchIntroducer(
	private val getMatchRecordDao: GetMatchRecordDao,
	private val saveMatchRecordPort: SaveMatchRecordPort,
	private val matchPoolPort: MatchPoolPort,
	private val regionProximityPort: RegionProximityPort,
) {

	/**
	 * [targetId] 사용자에게 소개 상대를 찾아 소개를 생성한다. 소개한 상대 userId를 반환하고, 후보를 못 찾으면 null.
	 * (호출 측은 반환된 상대 id로 "오늘 소개됨" 집합을 갱신해, 그 상대가 뒤이어 대상이 될 때 이중 소개를 막는다)
	 * [regionId]에서 가까운 지역 순서대로 반대 성별 풀을 순회해 재소개 이력 없는 첫 후보를 고른다.
	 * 소개에 성공하면 두 사람을 각자의 (성별, 지역) 풀에서 빼, 같은 배치에서 다시 소개되지 않게 한다.
	 * (상대의 지역은 [regionByUserId]로 알아내 그 사람의 지역 풀에서 제거한다)
	 */
	fun introduce(
		targetId: Long,
		gender: Gender,
		regionId: Long,
		regionByUserId: Map<Long, Long>,
		now: LocalDateTime,
	): Long? {
		val partnerGender: Gender = gender.opposite()

		// 가까운 지역부터 순회하며 첫 신선 후보를 찾는다. (각 지역 풀에서 최대 MAX_CANDIDATE_ATTEMPTS번 pop)
		val matchedPartnerId: Long = regionProximityPort.nearbyRegionIds(regionId)
			.firstNotNullOfOrNull { candidateRegionId: Long ->
				popFreshCandidate(
					targetId = targetId,
					gender = gender,
					pop = { matchPoolPort.pop(partnerGender, candidateRegionId) },
					pushBack = { ids: List<Long> -> matchPoolPort.pushBack(partnerGender, candidateRegionId, ids) },
				)
			}
			?: return null

		saveMatchRecordPort.saveProposedMatch(
			requesterId = targetId,
			requesterGender = gender,
			partnerId = matchedPartnerId,
			now = now,
		)

		// 소개된 두 사람을 각자의 (성별, 지역) 풀에서 제거해 같은 배치에서 다시 소개되지 않게 한다.
		matchPoolPort.remove(gender, regionId, targetId)
		regionByUserId[matchedPartnerId]?.let { partnerRegionId: Long ->
			matchPoolPort.remove(partnerGender, partnerRegionId, matchedPartnerId)
		}
		return matchedPartnerId
	}

	/**
	 * 주어진 [pop]/[pushBack]으로 한 풀에서 후보를 한 명씩 꺼내, 재소개 이력이 없는 첫 후보를 고른다.
	 * 풀 전체를 끝까지 까지 않고 **최대 [MAX_CANDIDATE_ATTEMPTS]번만** 시도한다. (이력 있는 후보를 무한정 훑어
	 * 대상당 DB 조회가 O(풀 크기)로 폭증하는 것을 막는다) 이력이 있어 건너뛴 후보들은 풀에 되돌린다.
	 */
	private fun popFreshCandidate(
		targetId: Long,
		gender: Gender,
		pop: () -> Long?,
		pushBack: (List<Long>) -> Unit,
	): Long? {
		val rejected: MutableList<Long> = mutableListOf()
		var found: Long? = null
		try {
			var attempts = 0
			while (attempts < MAX_CANDIDATE_ATTEMPTS) {
				val candidate: Long = pop() ?: break
				attempts++
				// 남/녀 자리를 배치해 (maleUserId, femaleUserId) 쌍으로 재소개 이력을 확인한다.
				val maleId: Long = if (gender == Gender.MALE) targetId else candidate
				val femaleId: Long = if (gender == Gender.MALE) candidate else targetId
				if (getMatchRecordDao.existsByPair(maleId, femaleId)) {
					rejected.add(candidate)
					continue
				}
				found = candidate
				break
			}
		} finally {
			pushBack(rejected)
		}
		return found
	}

	companion object {
		/**
		 * 한 지역 풀에서 후보를 찾을 때 pop해 이력 검사를 시도하는 최대 횟수.
		 * 이력 있는 후보를 무한정 훑어 대상당 DB 조회가 폭증하는 것을 막는 상한이다.
		 * (pop이 무작위라 신선한 후보가 있으면 보통 몇 번 내에 걸린다)
		 */
		private const val MAX_CANDIDATE_ATTEMPTS = 3
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.MatchIntroducerTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/MatchIntroducer.kt \
        meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/MatchIntroducerTest.kt
git commit -m "feat(match): MatchIntroducer를 근접 지역 순회 방식으로 재작성"
```

---

### Task 7: `RunDailyMatchBatchService` → `regionId` + 근접 스냅샷 refresh

배치 본체에서 성별 폴백 풀 적재를 제거하고, 지역 키를 `regionId`로 바꾸며, 시작 시 근접 스냅샷을 갱신한다.

**Files:**
- Modify: `meeple-scheduler/.../command/application/RunDailyMatchBatchService.kt`

**Interfaces:**
- Consumes: `RegionProximityPort.refresh()` (Task 3), `MatchIntroducer.introduce(..., regionId: Long, regionByUserId: Map<Long, Long>, ...)` (Task 6), `MatchPoolGroup.group/shuffled` (Task 4), `ActiveUser.regionId`·`MatchBatchTarget.regionId` (Task 5).

- [ ] **Step 1: 생성자에 `RegionProximityPort` 추가** — `MatchPoolByGender` import 제거, `regionProximityPort` 주입

생성자 파라미터에 `private val regionProximityPort: RegionProximityPort,` 를 `saveMatchPoolPort` 다음에 추가하고, 상단 import에서 `com.org.meeple.scheduler.match.command.domain.MatchPoolByGender` 를 제거, `com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort` 를 추가한다.

- [ ] **Step 2: `run()` 시작에 refresh 추가** — `val now` 다음 줄에 삽입

```kotlin
		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)

		// 근접 지역 순서 스냅샷을 최신 regions로 갱신한다. (참조 데이터 변경 반영 — 이후 소개에서 재사용)
		regionProximityPort.refresh()
```

- [ ] **Step 3: `regionByUserId`·`regionCode` 분기·introduce 호출을 `regionId`로 교체**

`regionByUserId` 산출을 `regionId`로:

```kotlin
		// 매칭 후 상대의 지역 풀에서도 빼주기 위한 (userId -> regionId) 조회표. (추가 쿼리 없이 메모리에서 산출)
		val regionByUserId: Map<Long, Long> = activeUsers.associate { user: ActiveUser -> user.userId to user.regionId }
```

대상 루프의 `regionCode` 분기를 `regionId`로:

```kotlin
					val regionId: Long? = target.regionId
					if (regionId == null) {
						// 활동 지역 미입력은 매칭 풀에 들어갈 수 없어 대상 아님
						skipped++
						continue
					}
```

introduce 호출:

```kotlin
					// 가까운 지역 순서로 반대 성별 풀에서 재소개 이력 없는 후보를 찾아 소개한다. 후보가 없으면 이번엔 건너뛴다.
					val partnerId: Long? = matchIntroducer.introduce(id, gender, regionId, regionByUserId, now)
```

- [ ] **Step 4: `loadMatchPools`에서 성별 폴백 풀 적재 제거**

```kotlin
	/**
	 * 매칭 풀을 적재하고, 적재에 쓴 활성 유저 목록을 반환한다.
	 * 활성 유저에서 이미 매칭(MATCHED)된 사용자를 빼고, (성별, 지역) 그룹으로 묶어 적재 순서 편향을 없애려 한 번 섞어 Redis에 적재한다.
	 * 반환한 목록은 이후 대상 순회의 지역 조회표 산출에 재사용한다.
	 */
	private fun loadMatchPools(loginAfter: LocalDateTime, matchedUserIds: MatchedUserIds): List<ActiveUser> {
		val activeUsers: List<ActiveUser> = matchedUserIds.exclude(getActiveUserDao.findActiveUsers(loginAfter))

		val groups: List<MatchPoolGroup> = MatchPoolGroup.group(activeUsers)
		groups.forEach { group: MatchPoolGroup -> saveMatchPoolPort.save(group.shuffled()) }

		log.info(
			"활성 유저 매칭 풀 그룹핑 완료: groups={}, activeUsers={}, matchedExcluded={}",
			groups.size, activeUsers.size, matchedUserIds.size,
		)
		return activeUsers
	}
```

- [ ] **Step 5: 전체 scheduler/infra 컴파일 통과 확인**

Run: `./gradlew :meeple-scheduler:compileKotlin :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (Task 4~7로 배치 경로 일관성 회복)

- [ ] **Step 6: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/RunDailyMatchBatchService.kt
git commit -m "feat(match): 일일 배치를 regionId 기반 근접 매칭으로 전환"
```

---

### Task 8: 온보딩 경로 — 포트/리포지토리/어댑터/서비스 교체

온보딩 후보 조회를 `regionId` + 근접 순회 + 재소개 이력 제외로 바꾼다.

**Files:**
- Modify: `meeple-core/.../command/application/port/out/GetMatchCandidatePort.kt`
- Modify: `meeple-infra/.../match/command/repository/MatchUserJpaRepository.kt`
- Modify: `meeple-infra/.../match/command/adapter/MatchUserAdapter.kt`
- Modify: `meeple-core/.../command/application/RecommendMatchService.kt`

**Interfaces:**
- Consumes: `RegionProximityRegistry.nearbyRegionIds(regionId)` (Task 2), `MatchUser.regionId` (기존).
- Produces: `GetMatchCandidatePort.findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long?`, `MatchUserJpaRepository.findNearestCandidateInRegion(requesterId, gender, regionId, loginAfter, pageable): List<Long>`

- [ ] **Step 1: `GetMatchCandidatePort` 시그니처 교체**

```kotlin
package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 매칭 후보 조회 아웃포트.
 * 요청자에게 소개할 상대(반대 성별 · 가까운 지역 순 · 재소개 이력 없음) 후보를 찾는다.
 */
interface GetMatchCandidatePort {

	/**
	 * 요청자([requesterId])의 지역([regionId])에서 가까운 지역 순으로,
	 * 반대 성별([gender]) · 최근 로그인([loginAfter] 이후) · [requesterId]와 재소개 이력이 없는 후보 1명의 userId를 반환한다.
	 * 후보가 없으면 null. (가장 가까운 지역의 후보를 우선하며, 같은 지역 내에서는 최근 로그인 우선)
	 */
	fun findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long?
}
```

- [ ] **Step 2: `MatchUserJpaRepository` — 옛 후보 쿼리 2개 삭제, 근접 단일 지역 쿼리 추가**

`countCandidates`, `findCandidateUserIds` 두 `@Query` 메서드(라인 22~54)를 삭제하고 아래로 교체한다. (`findByUserId`/`updateLastLoginAt`/`deleteByUserId` 는 유지)

```kotlin
	/**
	 * 한 지역([regionId]) 안에서 반대 성별([gender])·최근 로그인([loginAfter] 이후) 후보 중,
	 * 요청자([requesterId])와 함께 소개된 이력이 없는(재소개 방지) 후보를 최근 로그인 우선으로 조회한다.
	 * 호출 측이 [pageable]로 1건만 가져가 "이 지역의 최근접 신선 후보"로 쓴다. (가까운 지역부터 지역 단위로 순회)
	 * 이력 제외는 두 사람이 한 매칭(solo_match)에 함께 참가한 적이 있는지로 판단한다. (소프트 삭제된 매칭은 @SQLRestriction으로 제외 — existsByPair와 동일 의미)
	 */
	@Query(
		"""
		select m.userId
		from MatchUserEntity m
		where m.gender = :gender
		  and m.regionId = :regionId
		  and m.lastLoginAt >= :loginAfter
		  and not exists (
		      select 1
		      from SoloMatchMemberEntity me
		      join SoloMatchMemberEntity other on other.matchId = me.matchId
		      where me.userId = :requesterId
		        and other.userId = m.userId
		  )
		order by m.lastLoginAt desc
		""",
	)
	fun findNearestCandidateInRegion(
		@Param("requesterId") requesterId: Long,
		@Param("gender") gender: Gender,
		@Param("regionId") regionId: Long,
		@Param("loginAfter") loginAfter: LocalDateTime,
		pageable: Pageable,
	): List<Long>
```

리포지토리 상단 KDoc도 "후보 조회는 가까운 지역부터 지역 단위로, 반대 성별·최근 로그인·재소개 이력 없음 조건으로 한다."로 갱신한다.

- [ ] **Step 3: `MatchUserAdapter` — 근접 순회 + 이력 제외로 교체**

생성자에 `RegionProximityRegistry` 주입, `findOneCandidate` 교체, `ThreadLocalRandom` import 제거.

```kotlin
package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.DeleteMatchUserPort
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.application.port.out.GetMatchUserPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchUserPort
import com.org.meeple.core.match.command.domain.MatchUser
import com.org.meeple.infra.match.command.entity.MatchUserEntity
import com.org.meeple.infra.match.command.mapper.applyFrom
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchUserJpaRepository
import com.org.meeple.infra.region.RegionProximityRegistry
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [MatchUserEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — 매칭 읽기 모델의 후보 조회/적재/삭제 out-port를 함께 구현)
 * 후보 선정([GetMatchCandidatePort])은 요청자 지역에서 가까운 지역부터 순회하며, 각 지역의 최근접 신선 후보를 match_user 단독 조회로 찾는다.
 * 적재/삭제/조회([SaveMatchUserPort]/[DeleteMatchUserPort]/[GetMatchUserPort])는 user 도메인 이벤트 동기화에 쓰인다.
 */
@Component
class MatchUserAdapter(
	private val matchUserJpaRepository: MatchUserJpaRepository,
	private val regionProximityRegistry: RegionProximityRegistry,
) : GetMatchCandidatePort, SaveMatchUserPort, GetMatchUserPort, DeleteMatchUserPort {

	/**
	 * 요청자 지역에서 가까운 지역 순으로 순회하며, 각 지역의 "반대 성별·최근 로그인·재소개 이력 없음" 후보 중
	 * 최근 로그인 1명을 찾는다. 가장 가까운 지역에서 먼저 찾으면 즉시 반환하고, 끝까지 없으면 null.
	 */
	override fun findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long? {
		val limitOne: Pageable = PageRequest.of(0, 1)
		return regionProximityRegistry.nearbyRegionIds(regionId)
			.firstNotNullOfOrNull { candidateRegionId: Long ->
				matchUserJpaRepository
					.findNearestCandidateInRegion(requesterId, gender, candidateRegionId, loginAfter, limitOne)
					.firstOrNull()
			}
	}

	// user_id 기준 upsert: 기존 행이 있으면 가변 필드만 갱신(UPDATE), 없으면 새 엔티티로 INSERT.
	override fun save(matchUser: MatchUser): MatchUser {
		val entity: MatchUserEntity = matchUserJpaRepository.findByUserId(matchUser.userId)
			?.also { it.applyFrom(matchUser) }
			?: matchUser.toEntity()
		return matchUserJpaRepository.save(entity).toDomain()
	}

	override fun updateLastLoginAt(userId: Long, lastLoginAt: LocalDateTime) {
		matchUserJpaRepository.updateLastLoginAt(userId, lastLoginAt)
	}

	override fun findByUserId(userId: Long): MatchUser? =
		matchUserJpaRepository.findByUserId(userId)?.toDomain()

	override fun deleteByUserId(userId: Long) {
		matchUserJpaRepository.deleteByUserId(userId)
	}
}
```

- [ ] **Step 4: `RecommendMatchService` — 새 시그니처로 호출**

`findOneCandidate` 호출부(라인 37~42)를 교체하고 주석을 갱신한다.

```kotlin
		// 가까운 지역의 반대 성별·재소개 이력 없는 후보만 소개한다. 후보가 없으면 null(이번엔 소개 생략).
		val candidateId: Long = getMatchCandidatePort.findOneCandidate(
			requesterId = profile.userId,
			gender = profile.partnerGender(),
			regionId = profile.regionId,
			loginAfter = loginAfter,
		)
			?: return null
```

- [ ] **Step 5: core/infra 컴파일 확인**

Run: `./gradlew :meeple-core:compileKotlin :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/port/out/GetMatchCandidatePort.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/RecommendMatchService.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/MatchUserJpaRepository.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/MatchUserAdapter.kt
git commit -m "feat(match): 온보딩 추천을 regionId 근접 + 재소개 이력 제외로 전환"
```

---

### Task 9: `MatchUserEntity` 인덱스 추가 + 마이그레이션 SQL

온보딩 후보 조회(`gender=` AND `region_id IN(...)` AND `last_login_at>=`)를 받칠 복합 인덱스를 추가한다. (`region_code` 인덱스는 팀용으로 유지)

**Files:**
- Modify: `meeple-infra/.../match/command/entity/MatchUserEntity.kt`
- Create: `docs/migration/match_user_region_id_index.sql`

- [ ] **Step 1: 엔티티 `indexes`에 한 줄 추가** — 기존 인덱스 유지, 아래 추가

`idx_nickname_gender_user_id` 줄 다음(또는 region_code 인덱스 다음)에 추가:

```kotlin
		// 온보딩 추천 후보 선정(반대 성별·같은 지역·최근 로그인)용 — regionId 기반
		Index(name = "idx_gender_region_id_last_login_at", columnList = "gender, region_id, last_login_at"),
```

- [ ] **Step 2: 마이그레이션 SQL 작성**

```sql
-- match_user: 거리 기반 1:1 매칭 온보딩 후보 조회(gender=, region_id IN(...), last_login_at>=) seek용 인덱스.
-- 기존 idx_gender_region_code_last_login_at 은 팀(2:2) 매칭이 계속 사용하므로 유지한다.
ALTER TABLE match_user
    ADD INDEX idx_gender_region_id_last_login_at (gender, region_id, last_login_at);
```

- [ ] **Step 3: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/entity/MatchUserEntity.kt \
        docs/migration/match_user_region_id_index.sql
git commit -m "feat(match): match_user에 (gender, region_id, last_login_at) 인덱스 추가"
```

---

### Task 10: 배치 통합 테스트 + 픽스처 `regionId` 정합 + 근접 시나리오

`RunDailyMatchBatchIntegrationTest`를 `regionId` 기준으로 고치고(regions 적재 포함), 가까운 지역 우선 시나리오를 추가한다.

**Files:**
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/scheduler/RunDailyMatchBatchIntegrationTest.kt`

**Interfaces:**
- Consumes: `RegionEntityFixture.create(...)`, `UserDetailEntityFixture.create(regionId = ...)`, `MatchUserEntityFixture.create(regionId = ...)` (기존 픽스처에 `regionId` 파라미터 존재), `QRegionEntity`.

- [ ] **Step 1: import 추가 + `persistActiveUser`를 regionId 기반으로 교체**

상단 import에 추가:

```kotlin
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
```

`persistActiveUser`를 `regionCode: Int` → `regionId: Long`로 바꾸고 UserDetail·MatchUser에 regionId를 싣는다. (배치 근접 조회는 regions에 좌표가 있어야 하므로 region 적재는 각 테스트에서 먼저 한다)

```kotlin
// 정식 가입(ACTIVE) + 최근 로그인 + 성별·지역이 채워진 매칭 대상 사용자를 만들고 userId를 반환한다.
private fun persistActiveUser(providerId: String, gender: Gender, regionId: Long): Long {
	val lastLoginAt: LocalDateTime = LocalDateTime.now().minusDays(1)
	val userId: Long = IntegrationUtil.persist(
		UserEntityFixture.create(
			providerId = providerId,
			status = UserStatus.ACTIVE,
			lastLoginAt = lastLoginAt,
		),
	).id!!
	IntegrationUtil.persist(
		UserDetailEntityFixture.create(userId = userId, gender = gender, regionId = regionId),
	)
	IntegrationUtil.persist(
		MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt),
	)
	return userId
}

// 좌표를 가진 지역 한 건을 적재하고 생성된 regionId를 반환한다. (근접 계산 입력 — 배치가 run() 시작에 refresh로 읽는다)
private fun persistRegion(sido: String, sigungu: String, latitude: Double, longitude: Double): Long {
	val region: RegionEntity = IntegrationUtil.persist(
		RegionEntityFixture.create(sido = sido, sigungu = sigungu, latitude = latitude, longitude = longitude),
	)
	return region.id!!
}
```

- [ ] **Step 2: 기존 4개 컨텍스트의 `regionCode = 1` 호출을 region 적재 + `regionId`로 교체**

각 `it` 시작에서 region을 먼저 적재해 그 id로 사용자를 만든다. 예) 첫 번째 컨텍스트:

```kotlin
		context("반대 성별·가까운 지역의 활성 사용자가 있으면") {
			it("두 사람을 PROPOSED(DAILY)로 소개한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", latitude = 37.5, longitude = 127.0)
				val maleId: Long = persistActiveUser(providerId = "p-male", gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistActiveUser(providerId = "p-female", gender = Gender.FEMALE, regionId = regionId)
				// ... (이하 단언 동일)
```

나머지 세 컨텍스트("반대 성별 후보가 없으면", "이미 성사(MATCHED)된 사용자는", "같은 배치 실행 안에서")도 동일하게 맨 앞에 `val regionId: Long = persistRegion(...)`를 추가하고 `persistActiveUser(..., regionId = regionId)`로 바꾼다. (각 it은 같은 지역 하나만 쓰면 충분)

- [ ] **Step 3: 근접 우선 시나리오 컨텍스트 추가** (describe("run") 안에 추가)

```kotlin
			context("가까운 지역과 먼 지역에 각각 후보가 있으면") {
				it("가까운 지역의 후보와 소개한다") {
					// 강남(기준)과 같은 좌표의 근거리 지역, 멀리 떨어진 지역을 둔다
					val nearRegionId: Long = persistRegion("서울특별시", "강남구", latitude = 37.50, longitude = 127.00)
					val farRegionId: Long = persistRegion("부산광역시", "해운대구", latitude = 35.16, longitude = 129.16)

					val maleId: Long = persistActiveUser(providerId = "p-male", gender = Gender.MALE, regionId = nearRegionId)
					val nearFemaleId: Long = persistActiveUser(providerId = "p-near", gender = Gender.FEMALE, regionId = nearRegionId)
					val farFemaleId: Long = persistActiveUser(providerId = "p-far", gender = Gender.FEMALE, regionId = farRegionId)

					val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

					result.recommended shouldBe 1
					// 남성은 같은(가까운) 지역의 여성과 소개되고, 먼 지역 여성과는 소개되지 않는다
					proposedMatchBetween(maleId, nearFemaleId).shouldNotBeNull()
					proposedMatchBetween(maleId, farFemaleId).shouldBeNull()
				}
			}
```

- [ ] **Step 4: `afterTest`에 regions 정리 추가**

`IntegrationUtil.deleteAll(QUserEntity.userEntity)` 다음 줄에 추가:

```kotlin
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
```

- [ ] **Step 5: 통합 테스트 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.scheduler.RunDailyMatchBatchIntegrationTest"`
Expected: PASS (Testcontainers MySQL/Redis 기동)

- [ ] **Step 6: 커밋**

```bash
git add meeple-api/src/test/kotlin/com/org/meeple/api/scheduler/RunDailyMatchBatchIntegrationTest.kt
git commit -m "test(match): 배치 통합 테스트를 regionId 근접 매칭으로 갱신"
```

---

### Task 11: 온보딩 후보 조회 통합 테스트 (근접 + 이력 제외)

`GetMatchCandidatePort`를 실 컨텍스트에서 호출해 "가까운 지역 우선"과 "재소개 이력 제외"를 검증한다.

**Files:**
- Create: `meeple-api/src/test/kotlin/com/org/meeple/api/match/GetMatchCandidateIntegrationTest.kt`

**Interfaces:**
- Consumes: `GetMatchCandidatePort` 빈, `RegionProximityRegistry` 빈(시드 후 refresh), 픽스처들, `QRegionEntity`/`QMatchUserEntity`/`QSoloMatchEntity`/`QSoloMatchMemberEntity`.

- [ ] **Step 1: 통합 테스트 작성**

```kotlin
package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.SoloMatchEntity
import com.org.meeple.infra.region.RegionProximityRegistry
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [GetMatchCandidatePort] 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL)에서 후보 조회를 직접 호출한다.
 * regions 좌표는 컨텍스트 기동 후 적재하므로, 각 시나리오에서 [RegionProximityRegistry.refresh]로 근접 스냅샷을 갱신한 뒤 호출한다.
 */
class GetMatchCandidateIntegrationTest(
	private val getMatchCandidatePort: GetMatchCandidatePort,
	private val regionProximityRegistry: RegionProximityRegistry,
) : AbstractIntegrationSupport({

	val loginAfter: LocalDateTime = LocalDateTime.now().minusWeeks(2)

	describe("findOneCandidate") {

		context("가까운 지역과 먼 지역에 후보가 있으면") {
			it("가까운 지역 후보를 반환한다") {
				val nearRegionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val farRegionId: Long = persistRegion("부산광역시", "해운대구", 35.16, 129.16)
				regionProximityRegistry.refresh()

				val requesterId = 1L
				val nearFemaleId: Long = persistMatchUser(userId = 10L, gender = Gender.FEMALE, regionId = nearRegionId)
				persistMatchUser(userId = 20L, gender = Gender.FEMALE, regionId = farRegionId)

				val candidateId: Long? = getMatchCandidatePort.findOneCandidate(
					requesterId = requesterId, gender = Gender.FEMALE, regionId = nearRegionId, loginAfter = loginAfter,
				)

				candidateId shouldBe nearFemaleId
			}
		}

		context("가장 가까운 후보가 이미 소개된 이력이 있으면") {
			it("그 후보를 제외하고 다음 후보를 반환한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				regionProximityRegistry.refresh()

				val requesterId = 1L
				val introducedFemaleId: Long = persistMatchUser(userId = 10L, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = LocalDateTime.now())
				val freshFemaleId: Long = persistMatchUser(userId = 20L, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = LocalDateTime.now().minusDays(1))
				// requester(1L)와 introducedFemale(10L)은 이미 소개된 이력(PROPOSED)이 있다
				persistProposedMatch(requesterId, introducedFemaleId)

				val candidateId: Long? = getMatchCandidatePort.findOneCandidate(
					requesterId = requesterId, gender = Gender.FEMALE, regionId = regionId, loginAfter = loginAfter,
				)

				candidateId shouldBe freshFemaleId
			}
		}

		context("근접 지역 어디에도 신선 후보가 없으면") {
			it("null을 반환한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				regionProximityRegistry.refresh()

				// 같은 성별만 있어 반대 성별 후보가 없다
				persistMatchUser(userId = 10L, gender = Gender.MALE, regionId = regionId)

				getMatchCandidatePort.findOneCandidate(
					requesterId = 1L, gender = Gender.FEMALE, regionId = regionId, loginAfter = loginAfter,
				).shouldBeNull()
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})

private fun persistRegion(sido: String, sigungu: String, latitude: Double, longitude: Double): Long {
	val region: RegionEntity = IntegrationUtil.persist(
		RegionEntityFixture.create(sido = sido, sigungu = sigungu, latitude = latitude, longitude = longitude),
	)
	return region.id!!
}

private fun persistMatchUser(
	userId: Long,
	gender: Gender,
	regionId: Long,
	lastLoginAt: LocalDateTime = LocalDateTime.now(),
): Long {
	IntegrationUtil.persist(
		MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt),
	)
	return userId
}

private fun persistProposedMatch(userIdA: Long, userIdB: Long) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
			status = MatchStatus.PROPOSED,
		),
	)
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdA, gender = Gender.MALE))
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdB, gender = Gender.FEMALE))
}
```

- [ ] **Step 2: 통합 테스트 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetMatchCandidateIntegrationTest"`
Expected: PASS

> 참고: `SoloMatchEntityFixture.create`/`SoloMatchMemberEntityFixture.create`의 파라미터명이 실제와 다르면(예: `memberKey`/`status`/`matchId`/`userId`/`gender`) 픽스처 시그니처에 맞춰 호출만 조정한다. 시그니처는 `RunDailyMatchBatchIntegrationTest`의 동일 픽스처 사용처(라인 145~152)를 참고한다.

- [ ] **Step 3: 커밋**

```bash
git add meeple-api/src/test/kotlin/com/org/meeple/api/match/GetMatchCandidateIntegrationTest.kt
git commit -m "test(match): 온보딩 후보 조회 근접·이력제외 통합 테스트 추가"
```

---

### Task 12: 전체 빌드·테스트 검증

전 모듈 컴파일과 기존 테스트(MatchUserSyncE2ETest·GetMeetingTabE2ETest 등 `regionCode` 유지 경로 포함)가 깨지지 않았는지 확인한다.

**Files:** 없음(검증 전용)

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 실패가 있으면 해당 테스트를 열어 원인 확인:
- `regionCode` 컬럼/필드는 유지되므로 기존 `regionCode=1` 사용 테스트(MatchUserSyncE2ETest, MatchUserTest, GetMeetingTabE2ETest)는 그대로 통과해야 한다. 깨지면 본 작업이 의도치 않게 `regionCode`를 건드린 것 → 되돌린다.
- 배치/온보딩 통합 테스트 실패 시 Task 10/11의 region 적재·refresh 누락 여부 확인.

- [ ] **Step 2: 최종 확인 커밋** (코드 변경이 없으면 생략) — 변경이 있었다면

```bash
git add -A
git commit -m "test(match): 거리 기반 1:1 매칭 전체 빌드 검증 정리"
```

---

## 완료 기준 (Definition of Done)

- `./gradlew build` 통과.
- 1:1 배치·온보딩 모두 `regionId` 기반 근접 순서로 후보를 고른다(랜덤·regionCode 미사용).
- 한 번 소개된 쌍은 배치·온보딩 양쪽에서 재소개되지 않는다.
- `regionCode`(팀용)·`UserDetail.regionCode`·`MatchProfileSnapshot.regionCode`·팀 DAO는 변경되지 않았다.
- 신규 유닛(RegionProximity, MatchIntroducer)·통합(배치, 온보딩 후보) 테스트가 통과한다.
- `docs/migration/match_user_region_id_index.sql`로 인덱스가 명시 적용 가능하다.
