# 추천 팀 배치: 유저별 재매칭 팀 제외 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 추천 팀 배치가 솔로 유저에게, 그 유저가 과거에 MATCHED됐던 상대 팀(team_id)을 다시 추천하지 않도록 한다.

**Architecture:** 기존 데이터에서 파생(접근 A). 신규 스키마 없음. 배치가 대상 유저들의 "과거 MATCHED 상대 팀" 맵을 한 번에 적재하고, 각 유저의 후보 선택에서 그 팀들을 제외한다. 과거 이력은 소프트 삭제된 `team_members`·`matched_teams`·`team_matches`에 흩어져 있어 네이티브 SQL로 `@SQLRestriction`을 우회해 읽고, 성사 여부는 `expires_at` 연장 흔적으로 판정한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / MySQL / Kotest(DescribeSpec) / Testcontainers(E2E)

## Global Constraints

- 응답·주석은 한국어. `meeple-backend`만 수정(프론트엔드 변경 금지).
- 타입 명시: 변수·반환·람다 파라미터 타입을 생략하지 않는다.
- 현재 시각은 `TimeGenerator`로 얻는다(`LocalDateTime.now()` 직접 호출 금지). 단 인프라 쿼리는 매칭의 자기 `introduced_date` 기준으로 비교하므로 시각 주입이 필요 없다.
- CQRS: 조회 경로는 부수효과 없음. 새 조회는 scheduler `query/dao` 인터페이스 + infra `query` 구현으로 분리한다.
- 조회 구현 우선순위 ①Spring Data ②QueryDSL ③JPQL이나, 본 건은 소프트 삭제 우회가 강제하는 예외로 **네이티브 SQL**을 쓴다(기존 사례: `GetMatchRecordDaoImpl`).
- 성사 판정 임계값은 core `TeamMatch.MATCHED_EXPIRATION_EXTENSION_YEARS = 100`에 묶인 50년이다.
- 스케줄러 도메인의 유닛 테스트는 `meeple-api/src/test/.../scheduler/match/`에 둔다(예: `TeamPoolTest`). 인프라 쿼리는 `meeple-api`의 E2E(Testcontainers)로 검증한다(infra·scheduler에는 테스트 소스셋이 없다).

---

### Task 1: `PreviouslyMatchedTeams` 읽기 모델 + `GetUserMatchHistoryDao` 포트

scheduler 쪽 조회 계약(읽기 모델 + DAO 인터페이스)을 만든다. 읽기 모델은 일급 컬렉션으로 조회 로직을 담아 유닛 테스트한다.

**Files:**
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dto/PreviouslyMatchedTeams.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dao/GetUserMatchHistoryDao.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/PreviouslyMatchedTeamsTest.kt`

**Interfaces:**
- Produces:
  - `class PreviouslyMatchedTeams(opponentTeamIdsByUser: Map<Long, Set<Long>>)` with `fun opponentTeamIdsOf(userId: Long): Set<Long>`
  - `interface GetUserMatchHistoryDao { fun findPreviouslyMatchedTeamIdsByUser(userIds: Set<Long>): PreviouslyMatchedTeams }`

- [ ] **Step 1: 읽기 모델 유닛 테스트 작성 (실패)**

`meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/PreviouslyMatchedTeamsTest.kt`:

```kotlin
package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PreviouslyMatchedTeamsTest : DescribeSpec({

    describe("opponentTeamIdsOf") {
        it("유저에 매핑된 상대 team_id 집합을 돌려준다") {
            val previouslyMatched = PreviouslyMatchedTeams(mapOf(1L to setOf(100L, 200L)))
            previouslyMatched.opponentTeamIdsOf(1L) shouldBe setOf(100L, 200L)
        }

        it("이력이 없는 유저는 빈 집합을 돌려준다") {
            val previouslyMatched = PreviouslyMatchedTeams(mapOf(1L to setOf(100L)))
            previouslyMatched.opponentTeamIdsOf(2L) shouldBe emptySet()
        }
    }
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.PreviouslyMatchedTeamsTest"`
Expected: 컴파일 실패(`PreviouslyMatchedTeams` 미정의).

- [ ] **Step 3: 읽기 모델 구현**

`meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dto/PreviouslyMatchedTeams.kt`:

```kotlin
package com.org.meeple.scheduler.match.query.dto

/**
 * 유저별로, 그 유저가 과거(소속했던 팀 기준) MATCHED됐던 상대 team_id 집합을 보관하는 읽기 모델.
 * 추천 배치가 후보 팀에서 "이미 매칭했던 상대"를 유저 단위로 제외하는 데 쓴다.
 */
class PreviouslyMatchedTeams(
    private val opponentTeamIdsByUser: Map<Long, Set<Long>>,
) {
    /** [userId]가 과거 MATCHED됐던 상대 team_id. 이력이 없으면 빈 집합. */
    fun opponentTeamIdsOf(userId: Long): Set<Long> =
        opponentTeamIdsByUser[userId] ?: emptySet()
}
```

- [ ] **Step 4: DAO 인터페이스 구현**

`meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dao/GetUserMatchHistoryDao.kt`:

```kotlin
package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams

/**
 * 유저의 과거 MATCHED 상대 팀 조회 dao. (조회 전용) 구현은 infra가 담당한다.
 *
 * 솔로 유저는 과거 소속 팀(team_members)·매칭(matched_teams·team_matches)이 모두 소프트 삭제 상태일 수 있으므로,
 * 구현은 deleted_at 필터 없이 조회한다. 성사 여부는 team_matches.expires_at 연장 흔적으로 판정한다.
 */
interface GetUserMatchHistoryDao {

    /**
     * 주어진 [userIds]가 과거에 소속했던 팀 기준으로 MATCHED된 적 있는 상대 team_id를 유저별로 묶어 반환한다.
     * [userIds]가 비면 빈 결과를 반환한다.
     */
    fun findPreviouslyMatchedTeamIdsByUser(userIds: Set<Long>): PreviouslyMatchedTeams
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.PreviouslyMatchedTeamsTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dto/PreviouslyMatchedTeams.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dao/GetUserMatchHistoryDao.kt \
        meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/PreviouslyMatchedTeamsTest.kt
git commit -m "feat(match): 유저별 과거 MATCHED 상대 팀 조회 포트·읽기 모델 추가"
```

---

### Task 2: `GetUserMatchHistoryDaoImpl` (infra 네이티브 SQL) + E2E 검증

소프트 삭제를 우회하는 네이티브 쿼리를 구현하고, Testcontainers E2E로 "성사 후 종료된 상대는 반환, 미성사 종료 상대는 미반환"을 검증한다. 접근 A의 핵심 취약점(우회·휴리스틱)을 실DB로 못박는다.

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetUserMatchHistoryDaoImpl.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/match/GetUserMatchHistoryDaoE2ETest.kt`

**Interfaces:**
- Consumes: `GetUserMatchHistoryDao`, `PreviouslyMatchedTeams` (Task 1)
- Produces: `@Component class GetUserMatchHistoryDaoImpl(entityManager: EntityManager) : GetUserMatchHistoryDao` (Spring 빈)

- [ ] **Step 1: E2E 테스트 작성 (실패)**

`meeple-api/src/test/kotlin/com/org/meeple/api/match/GetUserMatchHistoryDaoE2ETest.kt`:

```kotlin
package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetUserMatchHistoryDao
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 유저의 과거 MATCHED 상대 팀 조회(네이티브 SQL)를 실DB로 검증한다.
 * - 소프트 삭제된 과거 팀·매칭(@SQLRestriction 우회)도 읽는다.
 * - 성사(expires_at 연장)된 매칭의 상대만 반환하고, 미성사 종료 상대는 반환하지 않는다.
 */
class GetUserMatchHistoryDaoE2ETest : AbstractIntegrationSupport() {

    @Autowired
    private lateinit var dao: GetUserMatchHistoryDao

    // 소프트 삭제 상태의 team_members 행을 만든다. (솔로 유저가 된 뒤 과거 팀 기록)
    private fun persistDeletedMember(teamId: Long, userId: Long) {
        val member = TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.DEACTIVE)
        member.softDelete(LocalDateTime.of(2026, 1, 10, 0, 0))
        IntegrationUtil.persist(member)
    }

    // 두 팀을 묶은 종료(CLOSED·소프트삭제) 매칭. matched=true면 성사 흔적(expires_at +100년)을 남긴다.
    private fun persistEndedMatch(teamA: Long, teamB: Long, matched: Boolean) {
        val introduced = LocalDate.of(2026, 1, 1)
        val expiresAt: LocalDateTime =
            if (matched) LocalDateTime.of(2126, 1, 2, 0, 0) else LocalDateTime.of(2026, 1, 8, 0, 0)
        val header = TeamMatchEntity(
            memberKey = listOf(teamA, teamB).sorted().joinToString("-"),
            introducedDate = introduced,
            expiresAt = expiresAt,
            status = MatchStatus.CLOSED,
            matchType = TeamMatchType.RECOMMENDED,
            dateInitAmount = 40,
            dateAcceptAmount = 40,
        )
        header.softDelete(LocalDateTime.of(2026, 1, 10, 0, 0))
        IntegrationUtil.persist(header)
        val teamMatchId: Long = header.id!!
        listOf(teamA, teamB).forEach { teamId: Long ->
            val mt = MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamId, status = MatchedTeamStatus.DEACTIVE)
            mt.softDelete(LocalDateTime.of(2026, 1, 10, 0, 0))
            IntegrationUtil.persist(mt)
        }
    }

    init {
        describe("findPreviouslyMatchedTeamIdsByUser") {
            it("과거 성사(MATCHED) 종료된 상대만 반환하고, 미성사 종료 상대는 제외한다") {
                val userId = 9001L
                val myTeamId = 9100L
                val matchedOpponentId = 9200L
                val notMatchedOpponentId = 9300L
                persistDeletedMember(myTeamId, userId)
                persistEndedMatch(myTeamId, matchedOpponentId, matched = true)
                persistEndedMatch(myTeamId, notMatchedOpponentId, matched = false)

                val result = dao.findPreviouslyMatchedTeamIdsByUser(setOf(userId))

                result.opponentTeamIdsOf(userId) shouldBe setOf(matchedOpponentId)
            }

            it("userIds가 비면 빈 결과를 돌려준다") {
                dao.findPreviouslyMatchedTeamIdsByUser(emptySet()).opponentTeamIdsOf(1L) shouldBe emptySet()
            }
        }

        afterTest {
            IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
            IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
            IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetUserMatchHistoryDaoE2ETest"`
Expected: 컴파일 실패(`GetUserMatchHistoryDao` 빈 미존재 → 구현 필요).

- [ ] **Step 3: 네이티브 쿼리 구현**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetUserMatchHistoryDaoImpl.kt`:

```kotlin
package com.org.meeple.infra.match.query

import com.org.meeple.scheduler.match.query.dao.GetUserMatchHistoryDao
import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

/**
 * scheduler [GetUserMatchHistoryDao]의 구현. 유저가 과거 소속했던 팀이 MATCHED된 적 있는 상대 team_id를 조회한다.
 *
 * team_members·matched_teams·team_matches가 모두 @SQLRestriction("deleted_at is null")로 소프트 삭제를
 * 감추므로, 종료된 과거 이력을 읽으려면 네이티브 SQL로 우회한다. (QueryDSL/JPQL로는 @SQLRestriction 우회 불가)
 *
 * 성사 판정: 성사 시 expires_at을 +100년 연장(core MATCHED_EXPIRATION_EXTENSION_YEARS)하고 종료해도
 * 되돌리지 않으므로, 'expires_at > introduced_date + 50년'이면 그 매칭은 한 번 MATCHED된 것이다.
 * (정상 만료는 소개일+며칠 수준 → 50년이 안전한 분리점이며, 매칭의 자기 introduced_date 기준이라 실행 시점과 무관하게 정확)
 *
 * 인덱스: team_members.idx_user_id(IN seek) → matched_teams.idx_team_id → team_matches PK →
 * matched_teams.ux_team_match_id_team_id. 풀스캔/filesort 없음.
 */
@Component
class GetUserMatchHistoryDaoImpl(
    private val entityManager: EntityManager,
) : GetUserMatchHistoryDao {

    override fun findPreviouslyMatchedTeamIdsByUser(userIds: Set<Long>): PreviouslyMatchedTeams {
        if (userIds.isEmpty()) return PreviouslyMatchedTeams(emptyMap())
        val sql: String = """
            SELECT tmself.user_id, mt_opp.team_id
            FROM team_members tmself
            JOIN matched_teams mt_self ON mt_self.team_id = tmself.team_id
            JOIN team_matches t        ON t.id = mt_self.team_match_id
            JOIN matched_teams mt_opp  ON mt_opp.team_match_id = t.id AND mt_opp.team_id <> tmself.team_id
            WHERE tmself.user_id IN (:userIds)
              AND t.expires_at > t.introduced_date + INTERVAL 50 YEAR
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        val rows: List<Array<Any>> = entityManager
            .createNativeQuery(sql)
            .setParameter("userIds", userIds)
            .resultList as List<Array<Any>>
        val opponentTeamIdsByUser: Map<Long, Set<Long>> = rows
            .groupBy({ row: Array<Any> -> (row[0] as Number).toLong() }, { row: Array<Any> -> (row[1] as Number).toLong() })
            .mapValues { (_: Long, teamIds: List<Long>) -> teamIds.toSet() }
        return PreviouslyMatchedTeams(opponentTeamIdsByUser)
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetUserMatchHistoryDaoE2ETest"`
Expected: PASS (성사 상대 `9200`만 반환, 미성사 상대 `9300` 제외, 빈 입력은 빈 결과)

- [ ] **Step 5: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetUserMatchHistoryDaoImpl.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/match/GetUserMatchHistoryDaoE2ETest.kt
git commit -m "feat(match): 유저 과거 MATCHED 상대 팀 네이티브 조회 구현"
```

---

### Task 3: `RecommendedTeamBatchService`에 유저별 제외 통합 + 유닛 테스트

배치가 대상 유저들의 과거 MATCHED 상대 팀 맵을 적재하고, 각 유저의 후보 선택에서 제외하도록 수정한다.

**Files:**
- Modify: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/RecommendedTeamBatchService.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/RecommendedTeamBatchServiceTest.kt`

**Interfaces:**
- Consumes: `GetUserMatchHistoryDao`, `PreviouslyMatchedTeams` (Task 1), `GetUserMatchHistoryDaoImpl` 빈 (Task 2)

- [ ] **Step 1: 배치 유닛 테스트 작성 (실패)**

`meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/RecommendedTeamBatchServiceTest.kt`:

```kotlin
package com.org.meeple.scheduler.match

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.RecommendedTeamBatchService
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.match.command.application.port.out.RegionShuffler
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import com.org.meeple.scheduler.match.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.match.command.domain.RecommendedTeamBatchResult
import com.org.meeple.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.meeple.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.meeple.scheduler.match.query.dao.GetRecommendedTeamRecordDao
import com.org.meeple.scheduler.match.query.dao.GetUserMatchHistoryDao
import com.org.meeple.scheduler.match.query.dto.CandidateTeam
import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams
import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

class RecommendedTeamBatchServiceTest : DescribeSpec({

    val fixedNow: LocalDateTime = LocalDateTime.of(2026, 6, 27, 9, 0)

    // 단일 대상·고정 후보로 배치를 구성한다. saves에 replace 호출이 기록된다.
    fun service(
        target: RecommendableSoloUser,
        candidates: List<CandidateTeam>,
        previouslyMatched: PreviouslyMatchedTeams,
        saves: MutableList<Triple<Long, Long, LocalDate>>,
    ): RecommendedTeamBatchService =
        RecommendedTeamBatchService(
            getRecommendableSoloUserDao = object : GetRecommendableSoloUserDao {
                override fun findRecommendableSoloUsers(loginAfter: LocalDateTime): List<RecommendableSoloUser> = listOf(target)
            },
            getRecommendedTeamRecordDao = object : GetRecommendedTeamRecordDao {
                override fun findUserIdsRecommendedOn(date: LocalDate): Set<Long> = emptySet()
            },
            getCandidateTeamDao = object : GetCandidateTeamDao {
                override fun findCandidateTeams(): List<CandidateTeam> = candidates
            },
            getUserMatchHistoryDao = object : GetUserMatchHistoryDao {
                override fun findPreviouslyMatchedTeamIdsByUser(userIds: Set<Long>): PreviouslyMatchedTeams = previouslyMatched
            },
            saveRecommendedTeamPort = object : SaveRecommendedTeamPort {
                override fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate) {
                    saves.add(Triple(userId, teamId, recommendedDate))
                }
            },
            regionProximityPort = object : RegionProximityPort {
                override fun refresh() {}
                override fun nearbyRegionIds(regionId: Long): List<Long> = listOf(regionId)
            },
            regionShuffler = RegionShuffler { regionIds: List<Long> -> regionIds },
            timeGenerator = object : TimeGenerator {
                override fun now(): LocalDateTime = fixedNow
            },
            random = Random(0),
        )

    describe("run - 유저별 재매칭 제외") {
        val target = RecommendableSoloUser(userId = 1L, gender = Gender.MALE, regionId = 1L)

        it("과거 MATCHED된 상대 팀은 추천하지 않고 같은 권역의 다른 팀을 고른다") {
            val saves: MutableList<Triple<Long, Long, LocalDate>> = mutableListOf()
            val candidates: List<CandidateTeam> = listOf(
                CandidateTeam(teamId = 100L, gender = Gender.FEMALE, regionId = 1L),
                CandidateTeam(teamId = 200L, gender = Gender.FEMALE, regionId = 1L),
            )
            val previouslyMatched = PreviouslyMatchedTeams(mapOf(1L to setOf(100L)))

            service(target, candidates, previouslyMatched, saves).run()

            saves shouldContainExactly listOf(Triple(1L, 200L, LocalDate.of(2026, 6, 27)))
        }

        it("권역의 모든 후보가 과거 MATCHED 상대면 추천하지 않는다(skipped)") {
            val saves: MutableList<Triple<Long, Long, LocalDate>> = mutableListOf()
            val candidates: List<CandidateTeam> = listOf(CandidateTeam(teamId = 100L, gender = Gender.FEMALE, regionId = 1L))
            val previouslyMatched = PreviouslyMatchedTeams(mapOf(1L to setOf(100L)))

            val result: RecommendedTeamBatchResult = service(target, candidates, previouslyMatched, saves).run()

            saves shouldBe emptyList()
            result.recommended shouldBe 0
            result.skipped shouldBe 1
        }
    }
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.RecommendedTeamBatchServiceTest"`
Expected: 컴파일 실패(`RecommendedTeamBatchService` 생성자에 `getUserMatchHistoryDao` 인자 없음).

- [ ] **Step 3: 배치 서비스 수정**

`RecommendedTeamBatchService.kt` — import 추가(파일 상단 import 영역):

```kotlin
import com.org.meeple.scheduler.match.query.dao.GetUserMatchHistoryDao
import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams
```

생성자에 의존성 추가(`getCandidateTeamDao` 다음 줄):

```kotlin
    private val getCandidateTeamDao: GetCandidateTeamDao,
    private val getUserMatchHistoryDao: GetUserMatchHistoryDao,
    private val saveRecommendedTeamPort: SaveRecommendedTeamPort,
```

`run()`에서 `pool` 적재 직후, 루프 직전에 이력 맵 적재:

```kotlin
        val pool: TeamPool = TeamPool.of(getCandidateTeamDao.findCandidateTeams())
        // 대상 유저들이 과거 MATCHED됐던 상대 팀을 한 번에 적재한다. (유저별 재매칭 제외)
        val previouslyMatched: PreviouslyMatchedTeams =
            getUserMatchHistoryDao.findPreviouslyMatchedTeamIdsByUser(targets.map { user: RecommendableSoloUser -> user.userId }.toSet())
```

루프 안에서 후보 선택 호출에 제외 집합을 전달:

```kotlin
                val teamId: Long? = findNearestRandomTeam(target, pool, previouslyMatched.opponentTeamIdsOf(target.userId))
```

`findNearestRandomTeam` 시그니처·본문 교체:

```kotlin
    /** [target] 권역에서 가까운 순 상위 N권역을 무작위 순서로 뒤져, [excludedTeamIds]를 뺀 후보 팀이 있는 첫 권역에서 무작위 팀 1개를 고른다. (없으면 null) */
    private fun findNearestRandomTeam(target: RecommendableSoloUser, pool: TeamPool, excludedTeamIds: Set<Long>): Long? {
        // 팀은 동성 구성이므로, 요청자의 반대 성별 = 추천 팀의 성별.
        val teamGender: Gender = target.gender.opposite()
        val regionOrder: List<Long> = regionShuffler.shuffleNearest(regionProximityPort.nearbyRegionIds(target.regionId))
        for (regionId: Long in regionOrder) {
            val teamIds: List<Long> = pool.teamIdsOf(teamGender, regionId).filterNot { teamId: Long -> teamId in excludedTeamIds }
            if (teamIds.isNotEmpty()) return teamIds.random(random)
        }
        return null
    }
```

클래스 KDoc의 제외 관련 문장 갱신(28번째 줄 부근):

```kotlin
 * 후보가 없으면 건너뛴다. 각 유저가 과거 소속 팀으로 MATCHED됐던 상대 팀은 그 유저에게 다시 추천하지 않는다(유저별 재매칭 제외).
 * 한 사용자의 실패가 다른 사용자에 전파되지 않도록 격리한다.
```

- [ ] **Step 4: 유닛 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.scheduler.match.RecommendedTeamBatchServiceTest"`
Expected: PASS

- [ ] **Step 5: 전체 빌드·테스트 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (스프링 컨텍스트가 `GetUserMatchHistoryDaoImpl` 빈으로 배치 서비스를 주입)

- [ ] **Step 6: 커밋**

```bash
git add meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/RecommendedTeamBatchService.kt \
        meeple-api/src/test/kotlin/com/org/meeple/scheduler/match/RecommendedTeamBatchServiceTest.kt
git commit -m "feat(match): 추천 팀 배치에 유저별 재매칭 팀 제외 적용"
```

---

## 범위 밖 (명시)

- 배치의 **추천 기록 시점 제외**까지만. 이미 `recommended_teams`에 있던 행은 손대지 않는다(유저당 upsert라 다음 실행 때 자연 갱신).
- 전역적 "성사된 팀은 누구에게도 추천 안 함"은 이번 범위가 아니다.

## Self-Review

- **스펙 커버리지**: 신규 조회 DAO·읽기 모델(Task 1), 네이티브 SQL 구현 + `expires_at` 휴리스틱 + soft-delete 우회(Task 2), 배치 통합 + 유닛 테스트(Task 3), infra 통합 테스트(Task 2 E2E). 스펙의 "테스트 전략"·"동작 발생 경우"·"인덱스 효율"·"범위" 모두 대응됨.
- **Placeholder 스캔**: 모든 코드 블록은 실제 구현. TODO/TBD 없음.
- **타입 일관성**: `findPreviouslyMatchedTeamIdsByUser(Set<Long>): PreviouslyMatchedTeams`, `opponentTeamIdsOf(Long): Set<Long>`, `findNearestRandomTeam(target, pool, Set<Long>)` 세 태스크에서 동일하게 사용. 생성자 인자 순서(`getUserMatchHistoryDao`는 `getCandidateTeamDao`와 `saveRecommendedTeamPort` 사이)가 Task 3 본문과 유닛 테스트 픽스처에서 일치.
