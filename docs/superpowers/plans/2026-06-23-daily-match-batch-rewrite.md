# 거리 기반 일일 매칭 배치 재작성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 레거시 `RunDailyMatchBatchService`(Redis 풀 기반)를 제거하고, `regionProximityPort` + 인메모리 풀로 매일 정오에 도는 거리 기반 일일 매칭 배치를 새로 만든다.

**Architecture:** 배치 시작에 "2주 내 활성 + 오늘 미매칭 + 성사 상태 아님" 유저를 인덱스 범위로 한 번 적재해 `(성별, regionId)` 버킷의 순수 도메인 풀(`MatchPool`)을 만든다. 대상을 순회하며 `regionProximityPort.nearbyRegionIds`로 가까운 지역 버킷부터 후보를 꺼내 `existsByPair`로 재소개만 거르고 짝지으며, 매칭되면 두 사람을 풀에서 뺀다. Redis 일체 제거.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA + QueryDSL / Kotest(DescribeSpec) / Testcontainers(MySQL).

## Global Constraints

- 모든 변수·반환 타입·람다 파라미터 타입을 **명시**한다(표현식 본문 함수 포함).
- 현재 시각은 `LocalDateTime.now()` 직접 호출 금지. 애플리케이션/도메인은 주입받은 `TimeGenerator.now()`를 쓴다. (테스트 픽스처·통합 테스트의 `LocalDateTime.now()`는 예외)
- 헥사고날·CQS/CQRS·모듈 경계 유지: scheduler는 core/infra에 의존하지 않고 **자기 port/dao**만 정의, infra가 구현. 조회는 부수효과 없이.
- 쿼리는 **항상 인덱스 효율을 고려하고 풀 테이블 스캔을 회피**한다. `@Query`/QueryDSL 조인은 명시 `join … on`.
- **가독성 최우선.** 매칭 상태(가용 집합)는 `MatchPool` 한 곳에만 둔다. 제외 규칙은 서비스 상단에서 집합 연산으로 드러낸다.
- 제외 규칙: ① 재소개 방지(`existsByPair`, 쌍 단위) ② 오늘 매칭된 유저(`introduced_date = today`) ③ 성사(MATCHED) 상태 유저.
- 본 작업은 `main`이 아닌 작업 브랜치에서 수행한다. 시작 전 `git checkout -b feat/daily-match-rewrite`(현재 `feat/recommended-team`에서 분기).

---

### Task 1: `solo_matches` 인덱스 추가 (오늘 매칭/성사 조회용)

제외 조회(`introduced_date = today`, `status = MATCHED`)를 인덱스 seek로 받치기 위한 인덱스 2개를 추가한다.

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/SoloMatchEntity.kt`
- Create: `docs/migration/solo_matches_batch_indexes.sql`

- [ ] **Step 1: 엔티티에 `indexes` 추가** — `@Table`에 `indexes` 배열을 추가(기존 `uniqueConstraints` 유지)

`SoloMatchEntity.kt`의 `@Table(...)`를 아래로 바꾼다(`import jakarta.persistence.Index` 추가):

```kotlin
@Table(
	name = "solo_matches",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_member_key", columnNames = ["member_key"]),
	],
	indexes = [
		// 오늘 소개된 유저 제외(introduced_date = today) seek용
		Index(name = "idx_introduced_date", columnList = "introduced_date"),
		// 성사(status = MATCHED) 유저 제외 seek용
		Index(name = "idx_status", columnList = "status"),
	],
)
```

- [ ] **Step 2: 마이그레이션 SQL 작성**

```sql
-- solo_matches: 일일 배치의 제외 조회를 인덱스 seek로 받친다.
--   - introduced_date: "오늘 매칭된 유저" 제외
--   - status: "성사(MATCHED) 유저" 제외
ALTER TABLE solo_matches
    ADD INDEX idx_introduced_date (introduced_date),
    ADD INDEX idx_status (status);
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/SoloMatchEntity.kt \
        docs/migration/solo_matches_batch_indexes.sql
git commit -m "feat(match): solo_matches에 introduced_date·status 인덱스 추가"
```

---

### Task 2: `MatchableUser` read model

배치 대상이자 후보가 되는 활성 유저 한 명의 read model.

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dto/MatchableUser.kt`

**Interfaces:**
- Produces: `data class MatchableUser(val userId: Long, val gender: Gender, val regionId: Long, val lastLoginAt: LocalDateTime)`

- [ ] **Step 1: 작성**

```kotlin
package com.org.oneulsogae.scheduler.match.query.dto

import com.org.oneulsogae.common.user.Gender
import java.time.LocalDateTime

/**
 * 일일 배치의 대상이자 후보가 되는 활성 매칭 유저 read model.
 * 버킷 키(성별·지역)와 후보 정렬(최근 로그인)에 쓰므로 모두 non-null이다.
 */
data class MatchableUser(
	val userId: Long,
	val gender: Gender,
	val regionId: Long,
	val lastLoginAt: LocalDateTime,
)
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-scheduler:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dto/MatchableUser.kt
git commit -m "feat(match): 일일 배치 후보 read model MatchableUser 추가"
```

---

### Task 3: `MatchPool` 순수 도메인 (+ Kotest 유닛)

`(성별, regionId)` 버킷 + 가용 집합으로 "꺼내고 빼기"를 담는 순수 도메인.

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/domain/MatchPool.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/MatchPoolTest.kt`

**Interfaces:**
- Consumes: `MatchableUser` (Task 2).
- Produces: `MatchPool.of(users: List<MatchableUser>): MatchPool`, `freshCandidates(gender: Gender, regionId: Long): List<MatchableUser>`, `remove(user: MatchableUser)`, `contains(user: MatchableUser): Boolean`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.org.oneulsogae.scheduler.match

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.command.domain.MatchPool
import com.org.oneulsogae.scheduler.match.query.dto.MatchableUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class MatchPoolTest : DescribeSpec({

	val base: LocalDateTime = LocalDateTime.of(2026, 6, 23, 0, 0)
	fun user(id: Long, gender: Gender, regionId: Long, lastLoginAt: LocalDateTime): MatchableUser =
		MatchableUser(userId = id, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt)

	describe("freshCandidates") {

		it("같은 (성별, 지역) 버킷의 가용 후보를 최근 로그인순으로 돌려준다") {
			val older: MatchableUser = user(10L, Gender.FEMALE, 1L, base.minusDays(2))
			val newer: MatchableUser = user(11L, Gender.FEMALE, 1L, base.minusDays(1))
			val pool: MatchPool = MatchPool.of(listOf(older, newer))

			pool.freshCandidates(Gender.FEMALE, 1L) shouldBe listOf(newer, older)
		}

		it("다른 성별/지역은 섞이지 않는다") {
			val femaleRegion1: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val femaleRegion2: MatchableUser = user(11L, Gender.FEMALE, 2L, base)
			val maleRegion1: MatchableUser = user(12L, Gender.MALE, 1L, base)
			val pool: MatchPool = MatchPool.of(listOf(femaleRegion1, femaleRegion2, maleRegion1))

			pool.freshCandidates(Gender.FEMALE, 1L) shouldBe listOf(femaleRegion1)
		}
	}

	describe("remove") {

		it("제거된 유저는 freshCandidates·contains에서 빠진다") {
			val target: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val pool: MatchPool = MatchPool.of(listOf(target))

			pool.remove(target)

			pool.contains(target) shouldBe false
			pool.freshCandidates(Gender.FEMALE, 1L).shouldBeEmpty()
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.MatchPoolTest"`
Expected: FAIL — `MatchPool` 미존재 컴파일 에러.

- [ ] **Step 3: 구현 작성**

```kotlin
package com.org.oneulsogae.scheduler.match.command.domain

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.query.dto.MatchableUser

/**
 * 일일 매칭의 인메모리 후보 풀. `(성별, 지역)` 버킷(최근 로그인 내림차순) + 가용 userId 집합으로 구성한다.
 * 매칭된 유저를 [remove]로 가용에서 빼면 이후 [freshCandidates]·[contains]에서 즉시 제외된다. (배치의 "꺼내고 빼기"를 한 곳에 응집)
 * 프레임워크에 의존하지 않는다.
 */
class MatchPool private constructor(
	private val bucketsByKey: Map<BucketKey, List<MatchableUser>>,
	private val available: MutableSet<Long>,
) {

	/** [gender]·[regionId] 버킷에서 아직 가용한 후보를 최근 로그인순으로 돌려준다. */
	fun freshCandidates(gender: Gender, regionId: Long): List<MatchableUser> =
		(bucketsByKey[BucketKey(gender, regionId)] ?: emptyList())
			.filter { user: MatchableUser -> user.userId in available }

	/** 매칭된 [user]를 가용에서 제거한다. */
	fun remove(user: MatchableUser) {
		available.remove(user.userId)
	}

	/** [user]가 아직 가용한지(=이번 실행에서 아직 짝지어지지 않았는지). */
	fun contains(user: MatchableUser): Boolean =
		user.userId in available

	private data class BucketKey(val gender: Gender, val regionId: Long)

	companion object {

		/** 후보들을 (성별, 지역) 버킷(최근 로그인 내림차순)으로 묶어 풀을 만든다. */
		fun of(users: List<MatchableUser>): MatchPool {
			val bucketsByKey: Map<BucketKey, List<MatchableUser>> = users
				.sortedByDescending { user: MatchableUser -> user.lastLoginAt }
				.groupBy { user: MatchableUser -> BucketKey(user.gender, user.regionId) }
			val available: MutableSet<Long> = users.mapTo(mutableSetOf()) { user: MatchableUser -> user.userId }
			return MatchPool(bucketsByKey, available)
		}
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.MatchPoolTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/domain/MatchPool.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/MatchPoolTest.kt
git commit -m "feat(match): 인메모리 후보 풀 MatchPool 추가"
```

---

### Task 4: `GetMatchableUserDao` (조회) + infra 구현

2주 내 활성 유저를 match_user에서 인덱스 범위로 조회한다.

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetMatchableUserDao.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchableUserDaoImpl.kt`

**Interfaces:**
- Consumes: `MatchableUser` (Task 2).
- Produces: `GetMatchableUserDao.findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser>`

- [ ] **Step 1: dao 인터페이스 작성**

```kotlin
package com.org.oneulsogae.scheduler.match.query.dao

import com.org.oneulsogae.scheduler.match.query.dto.MatchableUser
import java.time.LocalDateTime

/**
 * 일일 배치의 후보 풀을 적재하는 dao. (조회 전용)
 * scheduler는 core/infra에 의존하지 않으므로 자기 dao만 정의하고, 구현은 match_user를 아는 infra가 담당한다.
 */
interface GetMatchableUserDao {

	/** [loginAfter] 이후 로그인한 매칭 유저를 최근 로그인순으로 조회한다. (배치 대상이자 후보) */
	fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser>
}
```

- [ ] **Step 2: infra 구현 작성** (QueryDSL, match_user 단독 조회)

```kotlin
package com.org.oneulsogae.infra.match.query

import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.scheduler.match.query.dao.GetMatchableUserDao
import com.org.oneulsogae.scheduler.match.query.dto.MatchableUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [GetMatchableUserDao]의 QueryDSL 구현. match_user 단독으로 활성 유저를 [MatchableUser]로 투영한다.
 * `last_login_at >= :loginAfter` 범위는 `idx_last_login_at_user_id`로 받쳐져 풀 테이블 스캔이 아니다. (활성 집합만 읽는다)
 */
@Component
class GetMatchableUserDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchableUserDao {

	override fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return queryFactory
			.select(
				Projections.constructor(
					MatchableUser::class.java,
					matchUser.userId,
					matchUser.gender,
					matchUser.regionId,
					matchUser.lastLoginAt,
				),
			)
			.from(matchUser)
			.where(matchUser.lastLoginAt.goe(loginAfter))
			.orderBy(matchUser.lastLoginAt.desc())
			.fetch()
	}
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-scheduler:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetMatchableUserDao.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchableUserDaoImpl.kt
git commit -m "feat(match): 활성 매칭 유저 조회 GetMatchableUserDao 추가"
```

---

### Task 5: `GetMatchRecordDao.findUserIdsIntroducedOn` 추가

"오늘 소개된 유저" 집합 조회를 dao에 추가한다.

**Files:**
- Modify: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetMatchRecordDao.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchRecordDaoImpl.kt`

**Interfaces:**
- Produces: `GetMatchRecordDao.findUserIdsIntroducedOn(date: LocalDate): Set<Long>` (기존 `existsByPair`·`findMatchedUserIds` 유지).

- [ ] **Step 1: dao 인터페이스에 메서드 추가** — `import java.time.LocalDate` 추가, `findMatchedUserIds()` 아래에 추가

```kotlin
	/**
	 * 주어진 날짜([date])에 소개된(solo_matches.introduced_date = date) 매칭의 참가자 userId 집합.
	 * "오늘 한 번이라도 매칭된 유저"를 신규 소개에서 제외하는 데 쓴다.
	 */
	fun findUserIdsIntroducedOn(date: LocalDate): Set<Long>
```

- [ ] **Step 2: infra 구현에 메서드 추가** — `import java.time.LocalDate` 추가, 클래스에 메서드 추가

```kotlin
	// introduced_date로 그 날짜 소개 헤더를 seek(idx_introduced_date)하고, 참가자와 명시 조인해 userId를 모은다. (소프트 삭제 행은 @SQLRestriction으로 제외)
	override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> {
		val soloMatch: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		val soloMatchMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		return queryFactory
			.select(soloMatchMember.userId)
			.from(soloMatch)
			.join(soloMatchMember).on(soloMatchMember.matchId.eq(soloMatch.id))
			.where(soloMatch.introducedDate.eq(date))
			.fetch()
			.toSet()
	}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-scheduler:compileKotlin :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetMatchRecordDao.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchRecordDaoImpl.kt
git commit -m "feat(match): 오늘 소개된 유저 조회 findUserIdsIntroducedOn 추가"
```

---

### Task 6: `DailyMatchBatchService` 작성 (+ 유닛 테스트)

새 배치 서비스를 **`@Service` 없이** 작성하고 가짜 의존으로 유닛 테스트한다. (이 시점엔 레거시 `RunDailyMatchBatchService`가 아직 유일한 빈이라 DI 충돌이 없다. `@Service`는 Task 7에서 부여)

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/DailyMatchBatchService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/DailyMatchBatchServiceTest.kt`

**Interfaces:**
- Consumes: `GetMatchableUserDao.findMatchableUsers` (Task 4), `GetMatchRecordDao.{existsByPair, findMatchedUserIds, findUserIdsIntroducedOn}` (Task 5), `MatchPool` (Task 3), `MatchableUser` (Task 2), `SaveMatchRecordPort.saveProposedMatch`, `RegionProximityPort.{refresh, nearbyRegionIds}`, `TimeGenerator.now`, `MatchBatchResult`, `RunDailyMatchBatchUseCase`.
- Produces: `class DailyMatchBatchService(...) : RunDailyMatchBatchUseCase` with `run(): MatchBatchResult`.

- [ ] **Step 1: 실패하는 유닛 테스트 작성** (가짜 dao/port)

```kotlin
package com.org.oneulsogae.scheduler.match

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.command.application.DailyMatchBatchService
import com.org.oneulsogae.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.match.command.domain.MatchBatchResult
import com.org.oneulsogae.scheduler.match.query.dao.GetMatchRecordDao
import com.org.oneulsogae.scheduler.match.query.dao.GetMatchableUserDao
import com.org.oneulsogae.scheduler.match.query.dto.MatchableUser
import com.org.oneulsogae.scheduler.match.query.dto.MatchedUserIds
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class DailyMatchBatchServiceTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 23, 12, 0)
	fun user(id: Long, gender: Gender, regionId: Long): MatchableUser =
		MatchableUser(userId = id, gender = gender, regionId = regionId, lastLoginAt = now.minusDays(1))

	describe("run") {

		it("가까운 지역의 반대 성별 후보와 짝짓는다") {
			val male: MatchableUser = user(1L, Gender.MALE, 1L)
			val nearFemale: MatchableUser = user(2L, Gender.FEMALE, 1L)
			val farFemale: MatchableUser = user(3L, Gender.FEMALE, 2L)
			val saved = FakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				FakeGetMatchableUserDao(listOf(male, nearFemale, farFemale)),
				FakeGetMatchRecordDao(),
				saved,
				FakeRegionProximityPort(mapOf(1L to listOf(1L, 2L))),
				FakeTimeGenerator(now),
			)

			val result: MatchBatchResult = service.run()

			result.recommended shouldBe 1
			saved.pairs shouldContainExactly listOf(1L to 2L)   // 가까운 지역(1)의 여성과
		}

		it("재소개 이력이 있는 후보는 건너뛴다") {
			val male: MatchableUser = user(1L, Gender.MALE, 1L)
			val introduced: MatchableUser = user(2L, Gender.FEMALE, 1L)
			val fresh: MatchableUser = user(3L, Gender.FEMALE, 1L)
			val saved = FakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				FakeGetMatchableUserDao(listOf(male, introduced, fresh)),
				FakeGetMatchRecordDao(existingPairs = setOf(1L to 2L)),
				saved,
				FakeRegionProximityPort(mapOf(1L to listOf(1L))),
				FakeTimeGenerator(now),
			)

			service.run()

			saved.pairs shouldContainExactly listOf(1L to 3L)
		}

		it("성사·오늘매칭 유저는 대상·후보에서 제외된다") {
			val male: MatchableUser = user(1L, Gender.MALE, 1L)
			val matchedFemale: MatchableUser = user(2L, Gender.FEMALE, 1L)   // 성사 상태
			val todayFemale: MatchableUser = user(3L, Gender.FEMALE, 1L)     // 오늘 매칭됨
			val saved = FakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				FakeGetMatchableUserDao(listOf(male, matchedFemale, todayFemale)),
				FakeGetMatchRecordDao(matchedUserIds = setOf(2L), introducedTodayUserIds = setOf(3L)),
				saved,
				FakeRegionProximityPort(mapOf(1L to listOf(1L))),
				FakeTimeGenerator(now),
			)

			val result: MatchBatchResult = service.run()

			result.recommended shouldBe 0   // 남은 후보가 없어 male은 짝을 못 찾음
		}

		it("같은 실행에서 한 사람을 두 번 짝짓지 않는다") {
			val male1: MatchableUser = user(1L, Gender.MALE, 1L)
			val male2: MatchableUser = user(2L, Gender.MALE, 1L)
			val female: MatchableUser = user(3L, Gender.FEMALE, 1L)
			val saved = FakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				FakeGetMatchableUserDao(listOf(male1, male2, female)),
				FakeGetMatchRecordDao(),
				saved,
				FakeRegionProximityPort(mapOf(1L to listOf(1L))),
				FakeTimeGenerator(now),
			)

			val result: MatchBatchResult = service.run()

			result.recommended shouldBe 1   // female은 한 번만 짝지어짐
		}
	}
})

private class FakeGetMatchableUserDao(private val users: List<MatchableUser>) : GetMatchableUserDao {
	override fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser> = users
}

private class FakeGetMatchRecordDao(
	private val existingPairs: Set<Pair<Long, Long>> = emptySet(),
	private val matchedUserIds: Set<Long> = emptySet(),
	private val introducedTodayUserIds: Set<Long> = emptySet(),
) : GetMatchRecordDao {
	override fun existsByPair(userIdA: Long, userIdB: Long): Boolean {
		val key: Pair<Long, Long> = listOf(userIdA, userIdB).sorted().let { it[0] to it[1] }
		return existingPairs.any { listOf(it.first, it.second).sorted().let { s -> s[0] to s[1] } == key }
	}
	override fun findMatchedUserIds(): MatchedUserIds = MatchedUserIds(matchedUserIds)
	override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> = introducedTodayUserIds
}

private class FakeSaveMatchRecordPort : SaveMatchRecordPort {
	val pairs: MutableList<Pair<Long, Long>> = mutableListOf()
	override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
		pairs.add(requesterId to partnerId)
	}
}

private class FakeRegionProximityPort(private val nearby: Map<Long, List<Long>>) : RegionProximityPort {
	override fun refresh() = Unit
	override fun nearbyRegionIds(regionId: Long): List<Long> = nearby[regionId] ?: emptyList()
}

private class FakeTimeGenerator(private val fixed: LocalDateTime) : TimeGenerator {
	override fun now(): LocalDateTime = fixed
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.DailyMatchBatchServiceTest"`
Expected: FAIL — `DailyMatchBatchService` 미존재 컴파일 에러.

> 참고: `TimeGenerator.now()` 시그니처는 scheduler `command/application/port/out/TimeGenerator.kt`를 확인해 맞춘다(반환 `LocalDateTime`).

- [ ] **Step 3: 서비스 구현 작성** — **`@Service` 없이** (Task 7에서 부여)

```kotlin
package com.org.oneulsogae.scheduler.match.command.application

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.command.application.port.`in`.RunDailyMatchBatchUseCase
import com.org.oneulsogae.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.match.command.domain.MatchBatchResult
import com.org.oneulsogae.scheduler.match.command.domain.MatchPool
import com.org.oneulsogae.scheduler.match.query.dao.GetMatchRecordDao
import com.org.oneulsogae.scheduler.match.query.dao.GetMatchableUserDao
import com.org.oneulsogae.scheduler.match.query.dto.MatchableUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunDailyMatchBatchUseCase] 구현. 매일 정오에 도는 거리 기반 일일 매칭 배치.
 *
 * "2주 내 활성 + 오늘 미매칭 + 성사 상태 아님" 유저를 한 번 적재해 [MatchPool]을 만들고,
 * 대상을 순회하며 [RegionProximityPort.nearbyRegionIds]로 가까운 지역부터 반대 성별 후보를 꺼내
 * 재소개 이력([GetMatchRecordDao.existsByPair])이 없는 첫 후보와 PROPOSED 소개를 만든다. 매칭된 두 사람은 풀에서 뺀다.
 * 한 사용자의 실패가 다른 사용자에 전파되지 않도록 대상 단위로 격리하고, 예외만 failed로 집계한다.
 */
class DailyMatchBatchService(
	private val getMatchableUserDao: GetMatchableUserDao,
	private val getMatchRecordDao: GetMatchRecordDao,
	private val saveMatchRecordPort: SaveMatchRecordPort,
	private val regionProximityPort: RegionProximityPort,
	private val timeGenerator: TimeGenerator,
) : RunDailyMatchBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): MatchBatchResult {
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

		var recommended = 0
		var skipped = 0
		var failed = 0
		for (target: MatchableUser in matchables) {
			if (!pool.contains(target)) continue // 이번 실행에서 이미 짝지어진 유저
			try {
				val partner: MatchableUser? = findNearestFreshPartner(target, pool)
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

		val result = MatchBatchResult(targets = matchables.size, recommended = recommended, skipped = skipped, failed = failed)
		log.info("일일 매칭 배치 완료: {}", result)
		return result
	}

	/** [target] 지역에서 가까운 순으로 반대 성별 풀을 뒤져, 재소개 이력이 없는 가장 가까운 후보를 찾는다. (없으면 null) */
	private fun findNearestFreshPartner(target: MatchableUser, pool: MatchPool): MatchableUser? {
		val partnerGender: Gender = target.gender.opposite()
		for (regionId: Long in regionProximityPort.nearbyRegionIds(target.regionId)) {
			for (candidate: MatchableUser in pool.freshCandidates(partnerGender, regionId)) {
				if (!getMatchRecordDao.existsByPair(target.userId, candidate.userId)) return candidate
			}
		}
		return null
	}

	companion object {
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.DailyMatchBatchServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/DailyMatchBatchService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/DailyMatchBatchServiceTest.kt
git commit -m "feat(match): 거리 기반 일일 매칭 서비스 DailyMatchBatchService 추가"
```

---

### Task 7: 레거시 제거 + 컷오버 (원자적 스왑)

레거시 Redis 배치 일체를 제거하고, `DailyMatchBatchService`를 `@Service`로 승격해 유일한 배치 빈으로 만든다. `MatchedUserIds`의 `ActiveUser` 결합을 끊고, 통합 테스트를 새 배치로 재작성한다. **이 태스크 종료 시 전체 빌드가 GREEN**이어야 한다(중간 상태는 컴파일/DI가 깨질 수 있음).

**Files:**
- Delete: `oneulsogae-scheduler/.../command/application/RunDailyMatchBatchService.kt`
- Delete: `oneulsogae-scheduler/.../command/application/MatchIntroducer.kt`
- Delete: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/MatchIntroducerTest.kt`
- Delete: `oneulsogae-scheduler/.../command/application/port/out/MatchPoolPort.kt`
- Delete: `oneulsogae-scheduler/.../command/application/port/out/SaveMatchPoolPort.kt`
- Delete: `oneulsogae-scheduler/.../command/domain/MatchPoolGroup.kt`
- Delete: `oneulsogae-infra/.../match/command/adapter/MatchRedisAdapter.kt`
- Delete: `oneulsogae-scheduler/.../query/dao/GetMatchBatchTargetDao.kt`
- Delete: `oneulsogae-infra/.../match/query/GetMatchBatchTargetDaoImpl.kt`
- Delete: `oneulsogae-scheduler/.../query/dao/GetActiveUserDao.kt`
- Delete: `oneulsogae-infra/.../user/query/GetActiveUserDaoImpl.kt`
- Delete: `oneulsogae-scheduler/.../query/dto/MatchBatchTarget.kt`
- Delete: `oneulsogae-scheduler/.../query/dto/MatchBatchCursor.kt`
- Delete: `oneulsogae-scheduler/.../query/dto/ActiveUser.kt`
- Modify: `oneulsogae-scheduler/.../command/application/DailyMatchBatchService.kt` (add `@Service`)
- Modify: `oneulsogae-scheduler/.../query/dto/MatchedUserIds.kt` (drop `exclude(List<ActiveUser>)` + import)
- Rewrite: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunDailyMatchBatchIntegrationTest.kt`

**Interfaces:**
- Produces: `DailyMatchBatchService`가 유일한 `RunDailyMatchBatchUseCase` 빈.

- [ ] **Step 1: 레거시 파일 삭제** (위 Delete 목록)

```bash
cd "$(git rev-parse --show-toplevel)"
git rm \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/RunDailyMatchBatchService.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/MatchIntroducer.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/MatchIntroducerTest.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/port/out/MatchPoolPort.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/port/out/SaveMatchPoolPort.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/domain/MatchPoolGroup.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/MatchRedisAdapter.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetMatchBatchTargetDao.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchBatchTargetDaoImpl.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetActiveUserDao.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetActiveUserDaoImpl.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dto/MatchBatchTarget.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dto/MatchBatchCursor.kt \
  oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dto/ActiveUser.kt
```

- [ ] **Step 2: `DailyMatchBatchService`에 `@Service` 부여** — import + 클래스 어노테이션 추가

`import org.springframework.stereotype.Service` 추가, 클래스 선언을 `@Service\nclass DailyMatchBatchService(`로.

- [ ] **Step 3: `MatchedUserIds`에서 `ActiveUser` 결합 제거** — `exclude` 메서드와 `ActiveUser` import 삭제. 결과 파일:

```kotlin
package com.org.oneulsogae.scheduler.match.query.dto

/**
 * 이미 성사(MATCHED)된 매칭에 속한 사용자 ID 집합. 신규 소개 대상·후보에서 제외하는 데 쓴다.
 */
data class MatchedUserIds(
	val values: Set<Long>,
) {
	val size: Int get() = values.size

	/** [userId]가 이미 성사된 매칭에 속해 있는지 여부. */
	fun contains(userId: Long): Boolean = userId in values
}
```

- [ ] **Step 4: `RunDailyMatchBatchIntegrationTest` 재작성** (Redis 제거, match_user+regions 기반, "오늘 매칭 제외" 추가)

```kotlin
package com.org.oneulsogae.api.scheduler

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.SoloMatchType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.match.command.domain.MatchMembers
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.SoloMatchEntityFixture
import com.org.oneulsogae.infra.fixture.SoloMatchMemberEntityFixture
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.oneulsogae.infra.match.command.entity.SoloMatchEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.region.entity.RegionEntity
import com.org.oneulsogae.scheduler.match.command.application.port.`in`.RunDailyMatchBatchUseCase
import com.org.oneulsogae.scheduler.match.command.domain.MatchBatchResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunDailyMatchBatchUseCase](DailyMatchBatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 배치가 시작 시 regionProximityPort.refresh()로 근접 스냅샷을 적재하므로, regions·match_user를 적재한 뒤 호출한다.
 */
class RunDailyMatchBatchIntegrationTest(
	private val runDailyMatchBatchUseCase: RunDailyMatchBatchUseCase,
) : AbstractIntegrationSupport({

	describe("run") {

		context("반대 성별·가까운 지역의 활성 유저가 있으면") {
			it("두 사람을 PROPOSED(DAILY)로 소개한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 1
				result.failed shouldBe 0
				val match: SoloMatchEntity = proposedMatchBetween(maleId, femaleId).shouldNotBeNull()
				match.matchType shouldBe SoloMatchType.DAILY
				match.introducedDate shouldBe LocalDate.now()
			}
		}

		context("반대 성별 후보가 없으면") {
			it("아무도 소개하지 못한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId1: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				persistMatchableUser(userId = 1002L, gender = Gender.MALE, regionId = regionId)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 0
				matchesInvolving(maleId1).shouldBeEmpty()
			}
		}

		context("이미 성사(MATCHED)된 유저는") {
			it("신규 소개 대상·후보에서 제외된다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				persistMatch(maleId, 9001L, status = MatchStatus.MATCHED, introducedDate = LocalDate.now().minusDays(3))

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}

		context("오늘 이미 매칭된 유저는") {
			it("신규 소개 대상·후보에서 제외된다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				// maleId가 오늘 다른 사람과 소개됨(introduced_date = today)
				persistMatch(maleId, 9001L, status = MatchStatus.PROPOSED, introducedDate = LocalDate.now())

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}

		context("같은 실행 안에서") {
			it("한 사람이 두 번 소개되지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				persistMatchableUser(userId = 1002L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1003L, gender = Gender.FEMALE, regionId = regionId)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 1
				matchesInvolving(femaleId).size shouldBe 1
			}
		}

		context("가까운 지역과 먼 지역에 후보가 있으면") {
			it("가까운 지역 후보와 소개한다") {
				val nearRegionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val farRegionId: Long = persistRegion("부산광역시", "해운대구", 35.16, 129.16)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = nearRegionId)
				val nearFemaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = nearRegionId)
				val farFemaleId: Long = persistMatchableUser(userId = 1003L, gender = Gender.FEMALE, regionId = farRegionId)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 1
				proposedMatchBetween(maleId, nearFemaleId).shouldNotBeNull()
				proposedMatchBetween(maleId, farFemaleId).shouldBeNull()
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

private fun persistMatchableUser(userId: Long, gender: Gender, regionId: Long): Long {
	IntegrationUtil.persist(
		MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId, lastLoginAt = LocalDateTime.now()),
	)
	return userId
}

private fun persistMatch(userIdA: Long, userIdB: Long, status: MatchStatus, introducedDate: LocalDate) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
			status = status,
			introducedDate = introducedDate,
		),
	)
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdA, gender = Gender.MALE))
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdB, gender = Gender.FEMALE))
}

private fun proposedMatchBetween(userIdA: Long, userIdB: Long): SoloMatchEntity? {
	val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
	return IntegrationUtil.getQuery()
		.selectFrom(match)
		.where(
			match.memberKey.eq(MatchMembers.memberKeyOf(listOf(userIdA, userIdB)))
				.and(match.status.eq(MatchStatus.PROPOSED)),
		)
		.fetchOne()
}

private fun matchesInvolving(userId: Long): List<SoloMatchEntity> {
	val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
	val member: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
	return IntegrationUtil.getQuery()
		.select(match)
		.from(member)
		.join(match).on(match.id.eq(member.matchId))
		.where(member.userId.eq(userId))
		.fetch()
}
```

> 픽스처 시그니처는 실제와 맞춘다(확인됨): `RegionEntityFixture.create(sido, sigungu, longitude, latitude, order)`(이름인자라 순서 무관), `MatchUserEntityFixture.create(userId, gender, regionId, lastLoginAt, …)`, `SoloMatchEntityFixture.create(memberKey, introducedDate, status, …)`, `SoloMatchMemberEntityFixture.create(matchId, userId, gender, …)`.

- [ ] **Step 5: 전체 빌드** (컴파일·DI·테스트 일관성 회복 확인)

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 실패 시:
- 삭제 누락 참조가 남았는지(`MatchPool`/배치 외 다른 코드가 삭제 대상 참조) 확인.
- 새 통합 테스트의 6개 컨텍스트가 GREEN인지(특히 "오늘 매칭 제외", "근접 우선").
- `AdminMatchBatchE2ETest`는 데이터 없이 `recommended >= 0`만 단언하므로 그대로 통과해야 한다.

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor(match): 레거시 Redis 일일 배치 제거, DailyMatchBatchService로 컷오버"
```

---

### Task 8: 스케줄 변경 (매일 정오)

매칭 배치 cron 기본값을 정오 1회로 바꾼다.

**Files:**
- Modify: `oneulsogae-api/src/main/resources/application.yml`

- [ ] **Step 1: cron 기본값 변경** — 라인 68~69를 아래로

```yaml
      # 매일 12:00:00 1회 (Asia/Seoul). 운영에서 ONEULSOGAE_MATCH_BATCH_CRON 환경변수로 덮어쓸 수 있다.
      cron: ${ONEULSOGAE_MATCH_BATCH_CRON:0 0 12 * * *}
```

- [ ] **Step 2: 컨텍스트 기동 확인** (cron 파싱 오류 없는지 — E2E가 컨텍스트를 띄움)

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminMatchBatchE2ETest"`
Expected: PASS

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-api/src/main/resources/application.yml
git commit -m "chore(match): 일일 매칭 배치 cron 기본값을 매일 정오(KST)로 변경"
```

---

### Task 9: 전체 빌드·검증

**Files:** 없음(검증 전용)

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 점검:
- 삭제된 레거시(`MatchIntroducer`/Redis 풀/배치 DAO·DTO) 참조가 어디에도 남지 않음.
- 신규 유닛(`MatchPoolTest`, `DailyMatchBatchServiceTest`) + 통합(`RunDailyMatchBatchIntegrationTest` 6종) + `AdminMatchBatchE2ETest` GREEN.
- 온보딩/팀 배치 등 기존 경로 회귀 없음.

- [ ] **Step 2: 최종 확인 커밋** (변경이 있었다면)

```bash
git add -A
git commit -m "test(match): 일일 배치 재작성 전체 빌드 검증"
```

---

## 완료 기준 (Definition of Done)

- `./gradlew build` 통과.
- 레거시 `RunDailyMatchBatchService` + Redis 풀 머신러리(MatchIntroducer/MatchPool*/MatchRedisAdapter/배치 DAO·DTO) 완전 제거.
- 새 `DailyMatchBatchService`가 매일 정오에 도는 유일한 배치 빈이며: 2주 내 활성 + 오늘 미매칭 + 성사 아님 유저를, 가까운 지역 순으로, 재소개 이력 없이 짝짓는다.
- 모든 배치 조회가 인덱스 친화적(활성=범위 스캔, existsByPair=유니크 seek, 오늘매칭/성사=신규 인덱스 seek).
- `docs/migration/solo_matches_batch_indexes.sql`로 인덱스 명시 적용 가능.
- 가독성: 제외는 서비스 상단 집합 연산, 매칭 상태는 `MatchPool` 한 곳.
