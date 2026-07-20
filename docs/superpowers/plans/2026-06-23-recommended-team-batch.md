# 근접 팀 추천 일일 배치 (RecommendedTeamBatch) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 팀 미소속 솔로 유저에게 가까운 권역의 반대 성별 ACTIVE 팀 1개를 랜덤 추천하는 일일 배치를, 솔로 매칭 배치와 동일한 방식으로 재구현한다.

**Architecture:** 기존 `RunRecommendTeamBatch*`(반대 성별 + 같은 권역코드)를 `RecommendedTeamBatch*`로 개명한 뒤, 알고리즘을 `SoloMatchBatchService`와 동일 골격(근접 권역 셔플 + 전체 1회 적재 + 인메모리 제외)으로 교체한다. 팀의 권역은 `teams.region_id`를 직접 사용하고, "오늘 이미 추천된 유저"는 인메모리 Set으로 제외한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA + QueryDSL / Kotest(DescribeSpec) / Testcontainers(MySQL) / 헥사고날 멀티모듈(oneulsogae-scheduler·infra·api).

## Global Constraints

- 모듈 의존 방향 준수: 배치 로직·포트·도메인·read model은 `oneulsogae-scheduler`(core 비의존), out-port 구현(어댑터/DaoImpl)은 `oneulsogae-infra`, Controller/Scheduler는 `oneulsogae-api`.
- CQRS: 조회는 `query/{dao,dto}`, 명령은 `command/{application,domain,port,adapter}`. 조회 경로 부수효과 금지.
- 타입 명시: 변수·반환 타입·람다 파라미터 타입 생략 금지(표현식 본문 포함).
- 현재 시각은 `TimeGenerator`(scheduler out-port) 주입으로만 얻는다. `LocalDateTime.now()` 직접 호출 금지.
- 도메인 모델 → Kotest 유닛(프레임워크 무관, 외부 의존은 파라미터 주입). api 경계 → E2E/통합(Testcontainers + `AbstractIntegrationSupport` + 픽스처, 리포지토리 직접 의존 금지).
- scheduler 도메인의 유닛/통합 테스트는 `oneulsogae-api/src/test/...`에 둔다(scheduler 모듈에 kotest 테스트 소스셋이 없음 — 기존 `MatchPoolTest`·`RunSoloMatchBatchIntegrationTest`와 동일).
- HTTP 엔드포인트 경로 `/admin/v1/teams/recommend-batch`와 cron 기본값 `0 0 4 * * *`는 유지한다. 클래스/프로퍼티 네이밍만 `RecommendedTeamBatch`로 통일한다.
- 스키마 변경 없음(`recommended_teams`·인덱스 그대로). 새 인덱스 추가하지 않음(once-per-run 소규모 스캔, 쓰기비용 회피).
- 통합/E2E 테스트는 Docker(Testcontainers MySQL)가 필요하다.

---

## File Structure

**oneulsogae-scheduler** (`src/main/kotlin/com/org/oneulsogae/scheduler/match/`)
- `command/application/port/in/RunRecommendTeamBatchUseCase.kt` → 개명 `RunRecommendedTeamBatchUseCase.kt`
- `command/application/RunRecommendTeamBatchService.kt` → 개명+재작성 `RecommendedTeamBatchService.kt`
- `command/domain/RecommendTeamBatchResult.kt` → 개명 `RecommendedTeamBatchResult.kt`
- `command/adapter/RecommendTeamBatchJob.kt` → 개명 `RecommendedTeamBatchJob.kt`
- `command/domain/TeamPool.kt` → **신규** (후보 팀 인메모리 버킷)
- `query/dto/CandidateTeam.kt` → **신규** (후보 팀 read model)
- `query/dto/RecommendableSoloUser.kt` → 수정 (`regionCode`→`regionId`)
- `query/dao/GetRecommendableSoloUserDao.kt` → 수정 (시그니처 변경)
- `query/dao/GetCandidateTeamDao.kt` → 수정 (시그니처 변경)
- `query/dao/GetRecommendedTeamRecordDao.kt` → **신규** (오늘 추천분 조회)

**oneulsogae-infra** (`src/main/kotlin/com/org/oneulsogae/infra/match/query/`)
- `GetRecommendableSoloUserDaoImpl.kt` → 수정
- `GetCandidateTeamDaoImpl.kt` → 수정
- `GetRecommendedTeamRecordDaoImpl.kt` → **신규**

**oneulsogae-api**
- `src/main/kotlin/com/org/oneulsogae/scheduler/match/RecommendTeamBatchScheduler.kt` → 개명 `RecommendedTeamBatchScheduler.kt`
- `src/main/kotlin/com/org/oneulsogae/api/admin/AdminRecommendTeamBatchController.kt` → 개명 `AdminRecommendedTeamBatchController.kt`
- `src/main/kotlin/com/org/oneulsogae/api/admin/response/RecommendTeamBatchResponse.kt` → 개명 `RecommendedTeamBatchResponse.kt`
- `src/main/resources/application.yml` → cron 키 개명
- `src/test/kotlin/com/org/oneulsogae/scheduler/match/TeamPoolTest.kt` → **신규** 유닛
- `src/test/kotlin/com/org/oneulsogae/api/scheduler/RunRecommendedTeamBatchIntegrationTest.kt` → **신규** 통합
- `src/test/kotlin/com/org/oneulsogae/api/admin/AdminRecommendTeamBatchE2ETest.kt` → 개명+갱신 `AdminRecommendedTeamBatchE2ETest.kt`

---

## Task 1: `RecommendedTeamBatch` 네이밍 통일 (동작 불변 개명)

행위 변경 없이 클래스·프로퍼티 이름만 `RecommendTeam*` → `RecommendedTeamBatch*`로 바꾼다. 이 태스크가 끝나면 프로젝트가 컴파일되고 기존 E2E가 그대로 통과해야 한다.

**Files:**
- Rename: 위 File Structure의 "개명" 9개 파일 (scheduler 4 + api 4 + test 1)
- Modify: `oneulsogae-api/src/main/resources/application.yml`

**Interfaces:**
- Produces: `RunRecommendedTeamBatchUseCase.run(): RecommendedTeamBatchResult`, `class RecommendedTeamBatchService`, `data class RecommendedTeamBatchResult(targets, recommended, skipped, failed: Int)`, `class RecommendedTeamBatchJob.run(): RecommendedTeamBatchResult?`, `class RecommendedTeamBatchScheduler`, `class AdminRecommendedTeamBatchController`, `data class RecommendedTeamBatchResponse`.

- [ ] **Step 1: 파일 git mv (히스토리 보존)**

```bash
cd /Users/inwookjung/IdeaProjects/meeple-backend
S=oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match
git mv $S/command/application/port/in/RunRecommendTeamBatchUseCase.kt $S/command/application/port/in/RunRecommendedTeamBatchUseCase.kt
git mv $S/command/application/RunRecommendTeamBatchService.kt        $S/command/application/RecommendedTeamBatchService.kt
git mv $S/command/domain/RecommendTeamBatchResult.kt                 $S/command/domain/RecommendedTeamBatchResult.kt
git mv $S/command/adapter/RecommendTeamBatchJob.kt                   $S/command/adapter/RecommendedTeamBatchJob.kt
A=oneulsogae-api/src/main/kotlin/com/org/oneulsogae
git mv $A/scheduler/match/RecommendTeamBatchScheduler.kt             $A/scheduler/match/RecommendedTeamBatchScheduler.kt
git mv $A/api/admin/AdminRecommendTeamBatchController.kt             $A/api/admin/AdminRecommendedTeamBatchController.kt
git mv $A/api/admin/response/RecommendTeamBatchResponse.kt          $A/api/admin/response/RecommendedTeamBatchResponse.kt
git mv oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminRecommendTeamBatchE2ETest.kt \
       oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminRecommendedTeamBatchE2ETest.kt
```

- [ ] **Step 2: 식별자 일괄 치환**

다음 8개 식별자를 위 이동한 파일 + 이들을 참조하는 모든 파일에서 치환한다. (참조처: `AdminRecommendedTeamBatchController`·`RecommendedTeamBatchResponse`·`RecommendedTeamBatchScheduler`가 서로/Job/Result를 참조)

| old | new |
|---|---|
| `RunRecommendTeamBatchUseCase` | `RunRecommendedTeamBatchUseCase` |
| `RunRecommendTeamBatchService` | `RecommendedTeamBatchService` |
| `RecommendTeamBatchResult` | `RecommendedTeamBatchResult` |
| `RecommendTeamBatchJob` | `RecommendedTeamBatchJob` |
| `RecommendTeamBatchScheduler` | `RecommendedTeamBatchScheduler` |
| `AdminRecommendTeamBatchController` | `AdminRecommendedTeamBatchController` |
| `RecommendTeamBatchResponse` | `RecommendedTeamBatchResponse` |
| `AdminRecommendTeamBatchE2ETest` | `AdminRecommendedTeamBatchE2ETest` |

확인 명령(개명 후 잔존 0이어야 함):

```bash
grep -rln -e "RunRecommendTeamBatch" -e "RecommendTeamBatchResult" -e "RecommendTeamBatchJob" -e "RecommendTeamBatchScheduler" -e "AdminRecommendTeamBatch" -e "RecommendTeamBatchResponse" --include="*.kt" .
```

`RecommendedTeamBatchService`(구 `RunRecommendTeamBatchService`)의 클래스 선언이 `: RunRecommendedTeamBatchUseCase`를 구현하고 `RecommendedTeamBatchResult`를 반환하도록 본문도 함께 치환됐는지 확인한다. (동작 로직은 이 태스크에서 변경하지 않음 — `regionCode`·`findOneCandidateTeamId` 그대로)

- [ ] **Step 3: cron 프로퍼티 키 개명**

`oneulsogae-api/src/main/resources/application.yml`에서 `recommend-team-batch:` 블록 키와 env 이름을 바꾼다. (들여쓰기 유지, 기본값 동일)

```yaml
    recommended-team-batch:
      # 팀 없는 솔로 유저에게 가까운 결성 팀 추천. 매일 04:00 (Asia/Seoul). 운영에서 ONEULSOGAE_RECOMMENDED_TEAM_BATCH_CRON 환경변수로 덮어쓸 수 있다.
      cron: ${ONEULSOGAE_RECOMMENDED_TEAM_BATCH_CRON:0 0 4 * * *}
```

그리고 `RecommendedTeamBatchScheduler`의 `@Scheduled(cron = "\${oneulsogae.match.recommended-team-batch.cron}", ...)`로 키를 맞춘다.

- [ ] **Step 4: 컴파일 검증**

Run: `./gradlew compileKotlin compileTestKotlin -q`
Expected: BUILD SUCCESSFUL (개명만 했으므로 컴파일 통과)

- [ ] **Step 5: 기존 E2E 통과 검증**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminRecommendedTeamBatchE2ETest" -q`
Expected: PASS (동작 불변 — 기존 "같은 권역" 시나리오 그대로 통과)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "rename(match): 팀 추천 배치 네이밍 RecommendedTeamBatch로 통일

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `TeamPool` 도메인 + 유닛 테스트

후보 팀을 `(gender, regionId)` 버킷으로 묶는 인메모리 풀을 추가한다. 추가 전용(아무도 아직 사용 안 함)이라 독립적으로 컴파일·테스트된다.

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dto/CandidateTeam.kt`
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/domain/TeamPool.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/TeamPoolTest.kt`

**Interfaces:**
- Produces: `data class CandidateTeam(teamId: Long, gender: Gender, regionId: Long)`; `TeamPool.of(teams: List<CandidateTeam>): TeamPool`, `TeamPool.teamIdsOf(gender: Gender, regionId: Long): List<Long>`.

- [ ] **Step 1: 실패하는 유닛 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/TeamPoolTest.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.command.domain.TeamPool
import com.org.oneulsogae.scheduler.match.query.dto.CandidateTeam
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class TeamPoolTest : DescribeSpec({

    fun team(id: Long, gender: Gender, regionId: Long): CandidateTeam =
        CandidateTeam(teamId = id, gender = gender, regionId = regionId)

    describe("teamIdsOf") {

        it("같은 (성별, 권역) 버킷의 teamId만 돌려준다") {
            val a: CandidateTeam = team(100L, Gender.FEMALE, 1L)
            val b: CandidateTeam = team(101L, Gender.FEMALE, 1L)
            val pool: TeamPool = TeamPool.of(listOf(a, b))

            pool.teamIdsOf(Gender.FEMALE, 1L) shouldContainExactlyInAnyOrder listOf(100L, 101L)
        }

        it("다른 성별/권역은 섞이지 않는다") {
            val femaleRegion1: CandidateTeam = team(100L, Gender.FEMALE, 1L)
            val femaleRegion2: CandidateTeam = team(101L, Gender.FEMALE, 2L)
            val maleRegion1: CandidateTeam = team(102L, Gender.MALE, 1L)
            val pool: TeamPool = TeamPool.of(listOf(femaleRegion1, femaleRegion2, maleRegion1))

            pool.teamIdsOf(Gender.FEMALE, 1L) shouldBe listOf(100L)
        }

        it("해당 버킷이 없으면 빈 리스트") {
            val pool: TeamPool = TeamPool.of(emptyList())

            pool.teamIdsOf(Gender.MALE, 9L).shouldBeEmpty()
        }
    }
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.TeamPoolTest" -q`
Expected: 컴파일 실패 (`CandidateTeam`/`TeamPool` 미존재)

- [ ] **Step 3: `CandidateTeam` 작성**

`oneulsogae-scheduler/.../query/dto/CandidateTeam.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.query.dto

import com.org.oneulsogae.common.user.Gender

/**
 * 추천 후보가 되는 ACTIVE(결성) 팀 read model.
 * 풀 버킷 키(팀 성별·팀 활동권역)만 담는다. (teams에서 모두 non-null)
 */
data class CandidateTeam(
    val teamId: Long,
    val gender: Gender,
    val regionId: Long,
)
```

- [ ] **Step 4: `TeamPool` 작성**

`oneulsogae-scheduler/.../command/domain/TeamPool.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.command.domain

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.query.dto.CandidateTeam

/**
 * 팀 추천 배치의 인메모리 후보 팀 풀. `(성별, 권역)` 버킷에 teamId를 담는다.
 * 한 팀은 여러 유저에게 중복 추천될 수 있으므로 풀에서 제거하지 않는다(읽기 전용). 프레임워크에 의존하지 않는다.
 */
class TeamPool private constructor(
    private val teamIdsByKey: Map<BucketKey, List<Long>>,
) {

    /** [gender]·[regionId] 버킷의 후보 teamId 목록. (없으면 빈 리스트) */
    fun teamIdsOf(gender: Gender, regionId: Long): List<Long> =
        teamIdsByKey[BucketKey(gender, regionId)] ?: emptyList()

    private data class BucketKey(val gender: Gender, val regionId: Long)

    companion object {

        /** 후보 팀을 (성별, 권역) 버킷으로 묶어 풀을 만든다. */
        fun of(teams: List<CandidateTeam>): TeamPool {
            val teamIdsByKey: Map<BucketKey, List<Long>> = teams
                .groupBy({ team: CandidateTeam -> BucketKey(team.gender, team.regionId) }, { team: CandidateTeam -> team.teamId })
            return TeamPool(teamIdsByKey)
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.TeamPoolTest" -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(match): 팀 추천 배치용 TeamPool·CandidateTeam 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 오늘 추천분 조회 dao (`GetRecommendedTeamRecordDao`)

"하루 1회" 멱등을 위해 특정 일자에 이미 추천받은 user_id 집합을 읽는 조회 포트와 infra 구현을 추가한다. 추가 전용이라 컴파일만 검증한다(동작은 Task 4 통합 테스트에서 검증).

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetRecommendedTeamRecordDao.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetRecommendedTeamRecordDaoImpl.kt`

**Interfaces:**
- Produces: `GetRecommendedTeamRecordDao.findUserIdsRecommendedOn(date: LocalDate): Set<Long>`.

- [ ] **Step 1: 조회 포트 작성**

`oneulsogae-scheduler/.../query/dao/GetRecommendedTeamRecordDao.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.query.dao

import java.time.LocalDate

/**
 * 팀 추천 적재 이력 조회 dao. (조회 전용 — 적재는 SaveRecommendedTeamPort가 담당) QueryDSL 구현은 infra가 담당한다.
 * "하루에 한 번" 멱등을 위해, 주어진 일자에 이미 추천된 유저를 신규 추천에서 제외하는 데 쓴다.
 */
interface GetRecommendedTeamRecordDao {

    /** recommended_teams.recommended_date = [date]인 user_id 집합. */
    fun findUserIdsRecommendedOn(date: LocalDate): Set<Long>
}
```

- [ ] **Step 2: infra 구현 작성**

`oneulsogae-infra/.../match/query/GetRecommendedTeamRecordDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.match.query

import com.org.oneulsogae.infra.match.command.entity.QRecommendedTeamEntity
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendedTeamRecordDao
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * scheduler [GetRecommendedTeamRecordDao]의 QueryDSL 구현. (조회 전용)
 * recommended_teams에서 recommended_date 동등 조건으로 user_id를 모은다. (유저당 1행이라 fan-out 없음)
 */
@Component
class GetRecommendedTeamRecordDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : GetRecommendedTeamRecordDao {

    override fun findUserIdsRecommendedOn(date: LocalDate): Set<Long> {
        val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
        return queryFactory
            .select(recommended.userId)
            .from(recommended)
            .where(recommended.recommendedDate.eq(date))
            .fetch()
            .toSet()
    }
}
```

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew :oneulsogae-infra:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(match): 오늘 추천분 조회 GetRecommendedTeamRecordDao 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 배치 알고리즘 교체 (근접 셔플 + 랜덤 + 오늘 제외)

read model·dao 시그니처를 근접 방식으로 바꾸고, 서비스를 솔로 배치와 동일 골격으로 재작성한다. 이 변경은 DTO·dao·서비스·infra DaoImpl·E2E가 서로 맞물려 동시에 바뀌어야 컴파일된다. 통합 테스트를 먼저 작성해 동작을 고정한다.

**Files:**
- Modify: `oneulsogae-scheduler/.../query/dto/RecommendableSoloUser.kt`
- Modify: `oneulsogae-scheduler/.../query/dao/GetRecommendableSoloUserDao.kt`
- Modify: `oneulsogae-scheduler/.../query/dao/GetCandidateTeamDao.kt`
- Modify: `oneulsogae-scheduler/.../command/application/RecommendedTeamBatchService.kt`
- Modify: `oneulsogae-scheduler/.../command/application/port/in/RunRecommendedTeamBatchUseCase.kt` (KDoc)
- Modify: `oneulsogae-infra/.../match/query/GetRecommendableSoloUserDaoImpl.kt`
- Modify: `oneulsogae-infra/.../match/query/GetCandidateTeamDaoImpl.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunRecommendedTeamBatchIntegrationTest.kt` (신규)
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminRecommendedTeamBatchE2ETest.kt` (갱신)

**Interfaces:**
- Consumes: `TeamPool.of`, `TeamPool.teamIdsOf` (Task 2); `CandidateTeam` (Task 2); `GetRecommendedTeamRecordDao.findUserIdsRecommendedOn` (Task 3); 재사용 `RegionProximityPort.refresh()`/`nearbyRegionIds(regionId: Long): List<Long>`, `RegionShuffler.shuffleNearest(List<Long>): List<Long>`, `SaveRecommendedTeamPort.replace(userId: Long, teamId: Long, recommendedDate: LocalDate)`, `TimeGenerator.today(): LocalDate`, `Gender.opposite(): Gender`.
- Produces: `RecommendableSoloUser(userId: Long, gender: Gender, regionId: Long)`; `GetRecommendableSoloUserDao.findRecommendableSoloUsers(): List<RecommendableSoloUser>`; `GetCandidateTeamDao.findCandidateTeams(): List<CandidateTeam>`.

- [ ] **Step 1: 통합 테스트 작성 (실패용)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunRecommendedTeamBatchIntegrationTest.kt`:

```kotlin
package com.org.oneulsogae.api.scheduler

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QRecommendedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.RecommendedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.TeamEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMemberEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.region.entity.RegionEntity
import com.org.oneulsogae.scheduler.match.command.application.port.`in`.RunRecommendedTeamBatchUseCase
import com.org.oneulsogae.scheduler.match.command.domain.RecommendedTeamBatchResult
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * [RunRecommendedTeamBatchUseCase](RecommendedTeamBatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 배치가 시작 시 regionProximityPort.refresh()로 근접 스냅샷을 적재하므로 regions·match_user·teams를 적재한 뒤 호출한다.
 * RegionShuffler는 TestRegionShufflerConfig가 항등(순서 유지)으로 고정 → 근접 우선이 결정적.
 */
class RunRecommendedTeamBatchIntegrationTest(
    private val runRecommendedTeamBatchUseCase: RunRecommendedTeamBatchUseCase,
) : AbstractIntegrationSupport({

    describe("run") {

        context("팀 없는 솔로 유저와 반대 성별·가까운 권역 ACTIVE 팀이 있으면") {
            it("그 유저에게 그 팀을 추천 적재한다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                val teamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 1
                result.failed shouldBe 0
                recommendationOf(soloUserId).shouldNotBeNull().teamId shouldBe teamId
            }
        }

        context("반대 성별 후보 팀이 없으면") {
            it("아무도 추천하지 못한다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                persistActiveTeam(gender = Gender.MALE, regionId = regionId) // 같은 성별 → 후보 아님

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 0
                recommendationOf(soloUserId).shouldBeNull()
            }
        }

        context("가까운 권역과 먼 권역에 후보 팀이 있으면") {
            it("가까운 권역의 팀을 추천한다") {
                val nearRegionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val farRegionId: Long = persistRegion("부산광역시", "해운대구", 35.16, 129.16)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = nearRegionId)
                val nearTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = nearRegionId)
                val farTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = farRegionId)

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 1
                val recommendedTeamId: Long = recommendationOf(soloUserId).shouldNotBeNull().teamId
                recommendedTeamId shouldBe nearTeamId
                (recommendedTeamId == farTeamId) shouldBe false
            }
        }

        context("오늘 이미 추천받은 유저는") {
            it("재실행해도 제외되어 기존 추천이 유지된다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)
                // 오늘 일자로 다른 팀(9999)이 이미 추천돼 있다.
                IntegrationUtil.persist(RecommendedTeamEntity(userId = soloUserId, teamId = 9999L, recommendedDate = LocalDate.now()))

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 0
                recommendationOf(soloUserId).shouldNotBeNull().teamId shouldBe 9999L // 덮어쓰지 않음
            }
        }

        context("이미 팀에 속한 유저는") {
            it("추천 대상에서 제외된다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val teamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)
                // 5001은 male이지만 어떤 팀(teamId)의 멤버 → 팀 미소속 아님 → 대상 아님
                val memberId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = memberId, status = TeamMemberStatus.ACTIVE))
                // 추천 가능한 반대 성별 팀도 둔다 (대상이었다면 추천됐을 것)
                persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 0
                recommendationOf(memberId).shouldBeNull()
            }
        }
    }

    afterTest {
        IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
        IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
        IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
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

private fun persistSoloUser(userId: Long, gender: Gender, regionId: Long): Long {
    IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
    return userId
}

private fun persistActiveTeam(gender: Gender, regionId: Long): Long {
    val team: TeamEntity = IntegrationUtil.persist(
        TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "소개", status = TeamStatus.ACTIVE),
    )
    return team.id!!
}

private fun recommendationOf(userId: Long): RecommendedTeamEntity? {
    val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
    return IntegrationUtil.getQuery().selectFrom(recommended).where(recommended.userId.eq(userId)).fetchOne()
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.RunRecommendedTeamBatchIntegrationTest" -q`
Expected: 컴파일 실패 (`findRecommendableSoloUsers`/`findCandidateTeams` 미존재, `RecommendableSoloUser.regionId` 미존재). 이후 스텝으로 구현한다.

- [ ] **Step 3: `RecommendableSoloUser`에 regionId 적용**

`oneulsogae-scheduler/.../query/dto/RecommendableSoloUser.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.scheduler.match.query.dto

import com.org.oneulsogae.common.user.Gender

/**
 * 팀 추천을 받을 솔로 유저 read model. (match_user에 있으나 어떤 팀에도 속하지 않은 유저)
 * 근접 권역 계산·후보 팀 성별 선정에 필요한 성별·권역만 담는다. (둘 다 match_user에서 non-null)
 */
data class RecommendableSoloUser(
    val userId: Long,
    val gender: Gender,
    val regionId: Long,
)
```

- [ ] **Step 4: dao 시그니처 변경**

`oneulsogae-scheduler/.../query/dao/GetRecommendableSoloUserDao.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.scheduler.match.query.dao

import com.org.oneulsogae.scheduler.match.query.dto.RecommendableSoloUser

/**
 * 팀 추천 대상(팀 미소속 솔로 유저) 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * match_user에 있으나 비삭제 team_members 행이 전혀 없는 유저를 전부 반환한다. (솔로 배치와 동일하게 전체 1회 적재)
 */
interface GetRecommendableSoloUserDao {

    fun findRecommendableSoloUsers(): List<RecommendableSoloUser>
}
```

`oneulsogae-scheduler/.../query/dao/GetCandidateTeamDao.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.scheduler.match.query.dao

import com.org.oneulsogae.scheduler.match.query.dto.CandidateTeam

/**
 * 추천 후보 팀 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * 결성(ACTIVE) 팀 전체를 teamId·성별·활동권역으로 반환한다. (인메모리 TeamPool 구성용)
 */
interface GetCandidateTeamDao {

    fun findCandidateTeams(): List<CandidateTeam>
}
```

- [ ] **Step 5: 서비스 재작성**

`oneulsogae-scheduler/.../command/application/RecommendedTeamBatchService.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.scheduler.match.command.application

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.command.application.port.`in`.RunRecommendedTeamBatchUseCase
import com.org.oneulsogae.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.RegionShuffler
import com.org.oneulsogae.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.match.command.domain.RecommendedTeamBatchResult
import com.org.oneulsogae.scheduler.match.command.domain.TeamPool
import com.org.oneulsogae.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendedTeamRecordDao
import com.org.oneulsogae.scheduler.match.query.dto.RecommendableSoloUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.random.Random

/**
 * [RunRecommendedTeamBatchUseCase] 구현. 매일 도는 근접 기반 팀 추천 배치. (솔로 매칭 배치와 동일 골격)
 *
 * 시작 시 regionProximityPort.refresh()로 근접 스냅샷을 최신화하고, "오늘 이미 추천된 유저"를 인메모리 집합으로 제외한다.
 * 팀 미소속 솔로 유저를 전체 1회 적재해 순회하며, 각자에게 [RegionProximityPort.nearbyRegionIds]로 가까운 권역부터
 * 반대 성별 ACTIVE 팀을 찾아([RegionShuffler]로 근접 상위 N권역을 섞음) 그 권역의 팀 1개를 무작위로 골라 추천 적재(교체)한다.
 * 후보가 없으면 건너뛴다. 이전 매칭/추천 이력은 필터링하지 않는다(과거 이력이 있어도 추천). 한 사용자의 실패가 다른 사용자에 전파되지 않도록 격리한다.
 */
@Service
class RecommendedTeamBatchService(
    private val getRecommendableSoloUserDao: GetRecommendableSoloUserDao,
    private val getRecommendedTeamRecordDao: GetRecommendedTeamRecordDao,
    private val getCandidateTeamDao: GetCandidateTeamDao,
    private val saveRecommendedTeamPort: SaveRecommendedTeamPort,
    private val regionProximityPort: RegionProximityPort,
    private val regionShuffler: RegionShuffler,
    private val timeGenerator: TimeGenerator,
    private val random: Random = Random.Default,
) : RunRecommendedTeamBatchUseCase {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun run(): RecommendedTeamBatchResult {
        val today: LocalDate = timeGenerator.today()

        // 근접 스냅샷을 최신화한다. (가까운 권역 순서 계산의 기준)
        regionProximityPort.refresh()

        // 하루 1회: 오늘 이미 추천받은 유저는 제외한다. (재실행 멱등)
        val excluded: Set<Long> = getRecommendedTeamRecordDao.findUserIdsRecommendedOn(today)
        val targets: List<RecommendableSoloUser> = getRecommendableSoloUserDao.findRecommendableSoloUsers()
            .filterNot { user: RecommendableSoloUser -> user.userId in excluded }
        val pool: TeamPool = TeamPool.of(getCandidateTeamDao.findCandidateTeams())

        var recommended = 0
        var skipped = 0
        var failed = 0
        for (target: RecommendableSoloUser in targets) {
            try {
                val teamId: Long? = findNearestRandomTeam(target, pool)
                if (teamId == null) {
                    skipped++
                    continue
                }
                saveRecommendedTeamPort.replace(target.userId, teamId, today)
                recommended++
            } catch (e: Exception) {
                failed++
                log.warn("팀 추천 배치 처리 실패 userId={}", target.userId, e)
            }
        }

        val result: RecommendedTeamBatchResult = RecommendedTeamBatchResult(targets = targets.size, recommended = recommended, skipped = skipped, failed = failed)
        log.info("팀 추천 배치 완료: {}", result)
        return result
    }

    /** [target] 권역에서 가까운 순 상위 N권역을 무작위 순서로 뒤져, 후보 팀이 있는 첫 권역에서 무작위 팀 1개를 고른다. (없으면 null) */
    private fun findNearestRandomTeam(target: RecommendableSoloUser, pool: TeamPool): Long? {
        // 팀은 동성 구성이므로, 요청자의 반대 성별 = 추천 팀의 성별.
        val teamGender: Gender = target.gender.opposite()
        val regionOrder: List<Long> = regionShuffler.shuffleNearest(regionProximityPort.nearbyRegionIds(target.regionId))
        for (regionId: Long in regionOrder) {
            val teamIds: List<Long> = pool.teamIdsOf(teamGender, regionId)
            if (teamIds.isNotEmpty()) return teamIds.random(random)
        }
        return null
    }
}
```

- [ ] **Step 6: in-port KDoc 갱신**

`oneulsogae-scheduler/.../command/application/port/in/RunRecommendedTeamBatchUseCase.kt`의 KDoc을 새 동작에 맞춘다(시그니처는 동일):

```kotlin
package com.org.oneulsogae.scheduler.match.command.application.port.`in`

import com.org.oneulsogae.scheduler.match.command.domain.RecommendedTeamBatchResult

/**
 * 근접 팀 추천 일일 배치 인포트(유스케이스).
 * 팀 미소속 솔로 유저를 순회하며 가까운 권역의 반대 성별 ACTIVE 팀 1개를 무작위로 추천 적재(교체)한다.
 * 하루에 한 번만 추천하며(같은 날 재실행 시 이미 추천된 유저는 제외), 과거 매칭/추천 이력은 추천을 막지 않는다.
 * 개별 사용자 처리 실패가 전체 배치를 멈추지 않는다.
 */
interface RunRecommendedTeamBatchUseCase {

    fun run(): RecommendedTeamBatchResult
}
```

- [ ] **Step 7: infra `GetRecommendableSoloUserDaoImpl` 재작성**

`oneulsogae-infra/.../match/query/GetRecommendableSoloUserDaoImpl.kt` 전체 교체 (regionId 투영 + 전체 반환):

```kotlin
package com.org.oneulsogae.infra.match.query

import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.oneulsogae.scheduler.match.query.dto.RecommendableSoloUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * scheduler [GetRecommendableSoloUserDao]의 QueryDSL 구현. (조회 전용)
 * match_user 단독 베이스 + team_members NOT EXISTS로 팀 미소속 유저만 거른다.
 * (team_members @SQLRestriction이 소프트 삭제 행을 제외하므로, NOT EXISTS = 비삭제 소속이 전혀 없음 = 팀 미소속)
 * 솔로 매칭 배치와 동일하게 대상 전체를 한 번에 반환한다.
 */
@Component
class GetRecommendableSoloUserDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : GetRecommendableSoloUserDao {

    override fun findRecommendableSoloUsers(): List<RecommendableSoloUser> {
        val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
        val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

        return queryFactory
            .select(
                Projections.constructor(
                    RecommendableSoloUser::class.java,
                    matchUser.userId,
                    matchUser.gender,
                    matchUser.regionId,
                ),
            )
            .from(matchUser)
            .where(
                JPAExpressions.selectOne()
                    .from(teamMember)
                    .where(teamMember.userId.eq(matchUser.userId))
                    .notExists(),
            )
            .fetch()
    }
}
```

- [ ] **Step 8: infra `GetCandidateTeamDaoImpl` 재작성**

`oneulsogae-infra/.../match/query/GetCandidateTeamDaoImpl.kt` 전체 교체 (ACTIVE 팀 전체 → CandidateTeam):

```kotlin
package com.org.oneulsogae.infra.match.query

import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.oneulsogae.scheduler.match.query.dto.CandidateTeam
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * scheduler [GetCandidateTeamDao]의 QueryDSL 구현. (조회 전용)
 * teams(@SQLRestriction으로 소프트삭제 제외)에서 status=ACTIVE인 팀의 id·성별·활동권역(region_id)을 전부 반환한다.
 * 권역은 teams.region_id를 직접 쓴다. (팀당 1행 — 조인/fan-out 없음)
 */
@Component
class GetCandidateTeamDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : GetCandidateTeamDao {

    override fun findCandidateTeams(): List<CandidateTeam> {
        val team: QTeamEntity = QTeamEntity.teamEntity
        return queryFactory
            .select(
                Projections.constructor(
                    CandidateTeam::class.java,
                    team.id,
                    team.gender,
                    team.regionId,
                ),
            )
            .from(team)
            .where(team.status.eq(TeamStatus.ACTIVE))
            .fetch()
    }
}
```

- [ ] **Step 9: 통합 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.RunRecommendedTeamBatchIntegrationTest" -q`
Expected: PASS (5개 컨텍스트 모두)

- [ ] **Step 10: E2E 테스트 갱신**

`oneulsogae-api/.../api/admin/AdminRecommendedTeamBatchE2ETest.kt`를 새 동작(근접 권역 + region 적재 필요)에 맞춰 교체한다:

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QRecommendedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.RecommendedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.TeamEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.region.entity.RegionEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.greaterThanOrEqualTo

/**
 * `POST /admin/v1/teams/recommend-batch` E2E 테스트. (관리자 전용 근접 팀 추천 일일 배치 수동 실행)
 * 팀 없는 솔로 유저에게 가까운 권역의 반대 성별 ACTIVE 팀 1개를 추천 적재한다. ROLE_ADMIN만 접근 가능.
 */
class AdminRecommendedTeamBatchE2ETest : AbstractIntegrationSupport({

    fun persistRegion(): Long {
        val region: RegionEntity = IntegrationUtil.persist(
            RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.50, longitude = 127.00),
        )
        return region.id!!
    }

    fun persistActiveFemaleTeam(regionId: Long): Long {
        val team: TeamEntity = IntegrationUtil.persist(
            TeamEntity(name = "여성팀", gender = Gender.FEMALE, regionId = regionId, introduction = "즐겁게 만나요", status = TeamStatus.ACTIVE),
        )
        return team.id!!
    }

    fun recommendationOf(userId: Long): RecommendedTeamEntity? {
        val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
        return IntegrationUtil.getQuery().selectFrom(recommended).where(recommended.userId.eq(userId)).fetchOne()
    }

    describe("POST /admin/v1/teams/recommend-batch") {

        context("팀 없는 솔로 유저와 반대 성별·가까운 권역 ACTIVE 팀이 있으면") {
            it("그 유저에게 그 팀을 추천 적재한다 (200)") {
                val regionId: Long = persistRegion()
                val soloUserId = 4001L
                IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionId = regionId))
                val teamId: Long = persistActiveFemaleTeam(regionId)

                post("/admin/v1/teams/recommend-batch") {
                    bearer(adminAccessTokenFor(9101L))
                } expect {
                    status(200)
                    body("success", true)
                    body("data.recommended", greaterThanOrEqualTo(1))
                }

                val recommendation: RecommendedTeamEntity? = recommendationOf(soloUserId)
                (recommendation != null) shouldBe true
                recommendation!!.teamId shouldBe teamId
            }
        }

        context("오늘 이미 추천된 유저는") {
            it("재실행해도 추천 1행을 유지한다 (200)") {
                val regionId: Long = persistRegion()
                val soloUserId = 4002L
                IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionId = regionId))
                persistActiveFemaleTeam(regionId)

                post("/admin/v1/teams/recommend-batch") { bearer(adminAccessTokenFor(9102L)) } expect { status(200) }
                post("/admin/v1/teams/recommend-batch") { bearer(adminAccessTokenFor(9102L)) } expect { status(200) }

                val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
                val count: Long = IntegrationUtil.getQuery().select(recommended.count()).from(recommended)
                    .where(recommended.userId.eq(soloUserId)).fetchOne() ?: 0L
                count shouldBe 1L
            }
        }

        context("일반 사용자(ROLE_USER)가 호출하면") {
            it("403을 반환한다") {
                post("/admin/v1/teams/recommend-batch") { bearer(accessTokenFor(9103L)) } expect { status(403) }
            }
        }

        context("인증 토큰이 없으면") {
            it("401을 반환한다") {
                post("/admin/v1/teams/recommend-batch") {} expect { status(401) }
            }
        }
    }

    afterTest {
        IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
        IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
        IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
        IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
        IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
    }
})
```

- [ ] **Step 11: E2E 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminRecommendedTeamBatchE2ETest" -q`
Expected: PASS

- [ ] **Step 12: 전체 빌드·테스트**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL (전 모듈 컴파일 + 전체 테스트 통과)

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "feat(match): 팀 추천 배치를 근접 권역 셔플·랜덤 방식으로 재구현

- 팀 미소속 솔로 유저에게 가까운 권역의 반대 성별 ACTIVE 팀 1개를 랜덤 추천
- 하루 1회(오늘 추천분 인메모리 제외), 과거 이력은 추천을 막지 않음
- 팀 권역은 teams.region_id 직접 사용, 후보는 TeamPool로 인메모리 버킷팅

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (작성자 점검 완료)

**Spec coverage:**
- 대상=팀 미소속 솔로 유저 → Task 4 `GetRecommendableSoloUserDao`(NOT EXISTS) ✅
- 반대 성별 → Task 4 `findNearestRandomTeam`의 `target.gender.opposite()` ✅
- 근접 권역 셔플 재사용 → Task 4 `regionShuffler.shuffleNearest(nearbyRegionIds(...))` ✅
- 팀 권역=teams.region_id → Task 4 `GetCandidateTeamDaoImpl` ✅
- 유저당 1개 upsert → 재사용 `SaveRecommendedTeamPort.replace`(변경 없음) ✅
- 하루 1회 → Task 3 record dao + Task 4 인메모리 제외 ✅
- 이전 이력 무시 → Task 4: existsByPair/성사제외 없음 ✅
- 솔로 배치와 동일 방식(전체 적재+인메모리 제외) → Task 4 ✅
- 네이밍 통일 + HTTP 경로/cron 기본값 유지 → Task 1 ✅
- 테스트(TeamPool 유닛 + 통합 + E2E) → Task 2·4 ✅
- `RunRecommendTeamBatchUseCase` 완전 제거 → Task 1에서 개명(구 이름 잔존 0 확인) ✅

**Placeholder scan:** 모든 코드 스텝에 실제 코드 포함, TBD/TODO 없음 ✅

**Type consistency:** `findRecommendableSoloUsers(): List<RecommendableSoloUser>`, `findCandidateTeams(): List<CandidateTeam>`, `findUserIdsRecommendedOn(LocalDate): Set<Long>`, `TeamPool.teamIdsOf(Gender, Long): List<Long>`, `RecommendableSoloUser(userId, gender, regionId)` — Task 2·3·4 전반에서 일치 ✅
