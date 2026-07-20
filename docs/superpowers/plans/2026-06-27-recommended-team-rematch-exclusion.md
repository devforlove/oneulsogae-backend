# 추천 팀 배치: 유저별 재매칭 팀 제외 구현 플랜 (접근 B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 매칭 성사 시 (유저 → 상대 팀) 이력을 전용 테이블에 기록하고, 추천 배치가 그 이력으로 유저별 재매칭 상대를 제외한다.

**Architecture:** 성사 처리(`SendTeamInterestService.completeMatch`)와 같은 트랜잭션에서 `recommended_team_histories`에 기록(멱등). 배치는 유저별 단건 seek로 제외 집합을 얻어 후보에서 `filterNot`. 신규 스키마 1개, 네이티브 SQL·휴리스틱 없음.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / MySQL / Kotest(DescribeSpec) / Testcontainers(E2E)

## Global Constraints

- 응답·주석은 한국어. `meeple-backend`만 수정.
- 타입 명시: 변수·반환·람다 파라미터 타입을 생략하지 않는다.
- 도메인 로직 캡슐화: 서비스가 일급 컬렉션 내부를 들춰 계산하지 말고 도메인 메서드(`Teams.matchHistories()`)로 응집.
- 헥사고날: Controller→in-port, Service→out-port, infra Adapter가 out-port 구현. core port/out에 인터페이스, infra command/adapter에 구현.
- CQRS: 쓰기는 command(adapter), 조회는 query(daoImpl). 조회 경로 부수효과 없음.
- 영속성: 엔티티마다 어댑터 하나. 조회 구현 우선순위 ①Spring Data 파생 쿼리. infra query→command repository 참조 허용.
- 멱등: `recommended_team_histories`는 `UNIQUE(user_id, team_id)`. 저장은 `exists` 체크 후 신규만 insert(유니크 위반으로 성사 트랜잭션이 롤백되지 않도록).
- 쓰기는 성사와 **같은 트랜잭션**(`completeMatch` 본문). AFTER_COMMIT 이벤트 아님.
- 테스트: core 도메인→Kotest 유닛(`oneulsogae-api/src/test/.../domain/match`), scheduler→Kotest 유닛(`oneulsogae-api/src/test/.../scheduler/match`), api/infra 경계→E2E(`oneulsogae-api/src/test/.../api/match`, `AbstractIntegrationSupport` + `IntegrationUtil`). 테스트 스키마는 `ddl-auto: create-drop`이라 새 엔티티가 자동 생성된다.
- 작업 트리에 이전 세션의 무관한 미커밋 변경이 있다. 절대 건드리거나 스테이지하지 말 것. 각 태스크의 `git add`는 명시된 파일만.

---

### Task 1: core 도메인 이력 모델 + `Teams.matchHistories()` + SavePort

성사 시 남길 이력의 도메인 표현과, 두 팀에서 (구성원→상대 팀)을 만드는 도메인 메서드, 저장 out-port를 추가한다. Spring 컨텍스트 영향 없음(포트 구현은 Task 2).

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/RecommendedTeamHistory.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Teams.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/SaveRecommendedTeamHistoryPort.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamsTest.kt` (기존 파일에 describe 추가)

**Interfaces:**
- Produces:
  - `data class RecommendedTeamHistory(userId: Long, teamId: Long)` (teamId = 매칭한 상대 팀)
  - `Teams.matchHistories(): List<RecommendedTeamHistory>`
  - `interface SaveRecommendedTeamHistoryPort { fun saveAll(histories: List<RecommendedTeamHistory>) }`

- [ ] **Step 1: `Teams.matchHistories()` 유닛 테스트 작성 (실패)**

`TeamsTest.kt`의 `describe("opponentActiveMemberIds")` 블록 뒤(닫는 `})` 직전)에 추가. 파일 상단 import에 `import com.org.oneulsogae.core.match.command.domain.RecommendedTeamHistory` 추가:

```kotlin
	describe("matchHistories") {
		it("각 팀의 ACTIVE 구성원마다 (구성원 → 상대 팀 id) 이력을 만든다") {
			teams.matchHistories().map { history: RecommendedTeamHistory -> history.userId to history.teamId } shouldContainExactlyInAnyOrder
				listOf(1L to 2L, 2L to 2L, 3L to 1L, 4L to 1L)
		}

		it("INVITED 구성원은 제외한다") {
			val mixed = Teams(
				listOf(
					team(1L, 1L to TeamMemberStatus.ACTIVE, 2L to TeamMemberStatus.INVITED),
					team(2L, 3L to TeamMemberStatus.ACTIVE),
				),
			)
			mixed.matchHistories().map { history: RecommendedTeamHistory -> history.userId to history.teamId } shouldContainExactlyInAnyOrder
				listOf(1L to 2L, 3L to 1L)
		}
	}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamsTest"`
Expected: 컴파일 실패(`RecommendedTeamHistory`/`matchHistories` 미정의).

- [ ] **Step 3: 도메인 모델 구현**

`RecommendedTeamHistory.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.domain

/**
 * 매칭 성사 시 남기는 (유저 → 매칭한 상대 팀) 이력 한 건.
 * 추천 배치가 "이미 매칭한 상대 팀"을 그 유저에게 다시 추천하지 않도록 거르는 데 쓴다.
 */
data class RecommendedTeamHistory(
    val userId: Long,
    val teamId: Long,
)
```

- [ ] **Step 4: `Teams.matchHistories()` 추가**

`Teams.kt`의 `opponentTeamId` 함수 뒤(닫는 `}` 직전)에 추가:

```kotlin

	/** 성사된 두 팀의 ACTIVE 구성원마다 (그 구성원 → 상대 팀 id) 이력을 만든다. (재매칭 제외 기록용) */
	fun matchHistories(): List<RecommendedTeamHistory> =
		values.flatMap { team: Team ->
			team.activeMemberIds().map { userId: Long -> RecommendedTeamHistory(userId = userId, teamId = opponentTeamId(team.id)) }
		}
```

- [ ] **Step 5: SavePort 인터페이스 추가**

`SaveRecommendedTeamHistoryPort.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application.port.out

import com.org.oneulsogae.core.match.command.domain.RecommendedTeamHistory

/**
 * 성사 (유저 → 상대 팀) 이력 저장 out-port. infra 어댑터가 구현한다.
 * 이미 있는 (user_id, team_id)는 건너뛴다(멱등) — 같은 상대와 재매칭해도 유니크 위반으로 롤백되지 않도록.
 */
interface SaveRecommendedTeamHistoryPort {
    fun saveAll(histories: List<RecommendedTeamHistory>)
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamsTest"`
Expected: PASS (기존 케이스 + matchHistories 2건)

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/RecommendedTeamHistory.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Teams.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/SaveRecommendedTeamHistoryPort.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamsTest.kt
git commit -m "feat(match): 매칭 성사 이력 도메인 모델·저장 포트 추가"
```

---

### Task 2: infra 영속성 — 엔티티·리포지토리·어댑터 + 마이그레이션 + E2E

이력 테이블 엔티티와 멱등 저장 어댑터(`SaveRecommendedTeamHistoryPort` 구현)를 만들고, 저장·멱등을 실DB E2E로 검증한다.

**Files:**
- Create: `docs/migration/recommended_team_histories.sql`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/RecommendedTeamHistoryEntity.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/RecommendedTeamHistoryJpaRepository.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/RecommendedTeamHistoryAdapter.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/RecommendedTeamHistoryAdapterE2ETest.kt`

**Interfaces:**
- Consumes: `SaveRecommendedTeamHistoryPort`, `RecommendedTeamHistory` (Task 1)
- Produces:
  - `@Component RecommendedTeamHistoryAdapter : SaveRecommendedTeamHistoryPort` (Spring 빈)
  - `RecommendedTeamHistoryJpaRepository` with `existsByUserIdAndTeamId(userId, teamId): Boolean`, `findByUserId(userId): List<RecommendedTeamHistoryEntity>`
  - `QRecommendedTeamHistoryEntity` (빌드 시 생성)

- [ ] **Step 1: E2E 테스트 작성 (실패)**

`RecommendedTeamHistoryAdapterE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.core.match.command.application.port.out.SaveRecommendedTeamHistoryPort
import com.org.oneulsogae.core.match.command.domain.RecommendedTeamHistory
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.match.command.entity.QRecommendedTeamHistoryEntity
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.springframework.beans.factory.annotation.Autowired

/**
 * 매칭 성사 이력 저장 어댑터(멱등) 실DB 검증.
 * saveAll로 (유저 → 상대 팀)을 저장하고, 재호출 시 이미 있는 (user_id, team_id)는 중복 저장하지 않는다.
 */
class RecommendedTeamHistoryAdapterE2ETest : AbstractIntegrationSupport() {

    @Autowired
    private lateinit var saveRecommendedTeamHistoryPort: SaveRecommendedTeamHistoryPort

    init {
        describe("saveAll") {
            it("이력을 저장하고, 재호출 시 중복 (user_id, team_id)는 건너뛴다") {
                saveRecommendedTeamHistoryPort.saveAll(
                    listOf(
                        RecommendedTeamHistory(userId = 1L, teamId = 10L),
                        RecommendedTeamHistory(userId = 2L, teamId = 10L),
                    ),
                )
                // 재호출: (1,10) 중복 + (1,20) 신규
                saveRecommendedTeamHistoryPort.saveAll(
                    listOf(
                        RecommendedTeamHistory(userId = 1L, teamId = 10L),
                        RecommendedTeamHistory(userId = 1L, teamId = 20L),
                    ),
                )

                teamIdsOf(1L) shouldContainExactlyInAnyOrder listOf(10L, 20L)
                teamIdsOf(2L) shouldContainExactlyInAnyOrder listOf(10L)
            }
        }

        afterTest {
            IntegrationUtil.deleteAll(QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity)
        }
    }
}

private fun teamIdsOf(userId: Long): List<Long> {
    val q = QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity
    return IntegrationUtil.getQuery().select(q.teamId).from(q).where(q.userId.eq(userId)).fetch()
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.RecommendedTeamHistoryAdapterE2ETest"`
Expected: 컴파일 실패(`RecommendedTeamHistoryEntity`/`QRecommendedTeamHistoryEntity`/어댑터 빈 미존재).

- [ ] **Step 3: 엔티티 구현**

`RecommendedTeamHistoryEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.match.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 유저가 과거 매칭(MATCHED)한 상대 팀 이력. 추천 배치가 재매칭 상대를 제외하는 데 쓴다.
 * 성사 시점에 append-only로 기록한다(소프트 삭제 없음). UNIQUE(user_id, team_id)로 멱등 + 조회 seek.
 */
@Entity
@Table(
    name = "recommended_team_histories",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_user_id_team_id", columnNames = ["user_id", "team_id"]),
    ],
)
class RecommendedTeamHistoryEntity(
    /** 매칭한 유저. */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    /** 그 유저가 매칭한 상대 팀. */
    @Column(name = "team_id", nullable = false, updatable = false)
    val teamId: Long,
) : BaseEntity()
```

- [ ] **Step 4: 리포지토리 구현**

`RecommendedTeamHistoryJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.match.command.repository

import com.org.oneulsogae.infra.match.command.entity.RecommendedTeamHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RecommendedTeamHistoryJpaRepository : JpaRepository<RecommendedTeamHistoryEntity, Long> {

    /** 멱등 저장용: 이미 같은 (user_id, team_id)가 있는지. */
    fun existsByUserIdAndTeamId(userId: Long, teamId: Long): Boolean

    /** 조회용: 유저가 매칭한 상대 팀 행들. */
    fun findByUserId(userId: Long): List<RecommendedTeamHistoryEntity>
}
```

- [ ] **Step 5: 어댑터 구현**

`RecommendedTeamHistoryAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.match.command.adapter

import com.org.oneulsogae.core.match.command.application.port.out.SaveRecommendedTeamHistoryPort
import com.org.oneulsogae.core.match.command.domain.RecommendedTeamHistory
import com.org.oneulsogae.infra.match.command.entity.RecommendedTeamHistoryEntity
import com.org.oneulsogae.infra.match.command.repository.RecommendedTeamHistoryJpaRepository
import org.springframework.stereotype.Component

/**
 * [RecommendedTeamHistoryEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 성사 이력 저장 out-port를 구현한다. 이미 있는 (user_id, team_id)는 건너뛰어 멱등을 보장한다.
 * (같은 유저가 같은 상대와 재매칭해도 유니크 위반으로 성사 트랜잭션이 롤백되지 않게 한다)
 */
@Component
class RecommendedTeamHistoryAdapter(
    private val recommendedTeamHistoryJpaRepository: RecommendedTeamHistoryJpaRepository,
) : SaveRecommendedTeamHistoryPort {

    override fun saveAll(histories: List<RecommendedTeamHistory>) {
        histories.forEach { history: RecommendedTeamHistory ->
            if (!recommendedTeamHistoryJpaRepository.existsByUserIdAndTeamId(history.userId, history.teamId)) {
                recommendedTeamHistoryJpaRepository.save(
                    RecommendedTeamHistoryEntity(userId = history.userId, teamId = history.teamId),
                )
            }
        }
    }
}
```

- [ ] **Step 6: 마이그레이션 SQL 작성**

`docs/migration/recommended_team_histories.sql`:

```sql
CREATE TABLE recommended_team_histories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    team_id     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_user_id_team_id UNIQUE (user_id, team_id)
);
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.RecommendedTeamHistoryAdapterE2ETest"`
Expected: PASS (저장 + 멱등 재호출 후 user1→{10,20}, user2→{10})

- [ ] **Step 8: 커밋**

```bash
git add docs/migration/recommended_team_histories.sql \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/RecommendedTeamHistoryEntity.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/RecommendedTeamHistoryJpaRepository.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/RecommendedTeamHistoryAdapter.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/RecommendedTeamHistoryAdapterE2ETest.kt
git commit -m "feat(match): 매칭 성사 이력 테이블·멱등 저장 어댑터 추가"
```

---

### Task 3: 성사 처리에 이력 기록 통합 + 성사 플로우 E2E

`SendTeamInterestService.completeMatch()`가 성사 시 같은 트랜잭션에서 이력을 기록하도록 포트를 주입·호출하고, 성사 E2E에 이력 4행 검증을 추가한다.

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/SendTeamInterestService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendTeamInterestE2ETest.kt`

**Interfaces:**
- Consumes: `SaveRecommendedTeamHistoryPort` (Task 1), `Teams.matchHistories()` (Task 1), `RecommendedTeamHistoryAdapter` 빈 (Task 2), `QRecommendedTeamHistoryEntity` (Task 2)

- [ ] **Step 1: E2E에 이력 검증 추가 (실패)**

`SendTeamInterestE2ETest.kt` 수정:

(a) 파일 상단 import에 추가:
```kotlin
import com.org.oneulsogae.infra.match.command.entity.QRecommendedTeamHistoryEntity
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
```

(b) MATCHED 케이스(`it("수락 비용(40)이 차감되고 MATCHED가 된다 ...")`)의 `matchedAlarms(...)` 마지막 단언 뒤에 추가:
```kotlin
				// 성사 이력: 4인 각자 → 상대 팀 (재매칭 제외용)
				recommendedTeamHistoryTeamIds(myOwner) shouldContainExactlyInAnyOrder listOf(opponentTeamId)
				recommendedTeamHistoryTeamIds(myInvited) shouldContainExactlyInAnyOrder listOf(opponentTeamId)
				recommendedTeamHistoryTeamIds(oppOwner) shouldContainExactlyInAnyOrder listOf(myTeamId)
				recommendedTeamHistoryTeamIds(oppInvited) shouldContainExactlyInAnyOrder listOf(myTeamId)
```

(c) `afterTest` 블록 맨 앞에 정리 추가:
```kotlin
		IntegrationUtil.deleteAll(QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity)
```

(d) 파일 하단 private 헬퍼들 사이에 추가:
```kotlin
private fun recommendedTeamHistoryTeamIds(userId: Long): List<Long> {
	val q = QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity
	return IntegrationUtil.getQuery().select(q.teamId).from(q).where(q.userId.eq(userId)).fetch()
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendTeamInterestE2ETest"`
Expected: MATCHED 케이스에서 이력 행이 없어 실패(`shouldContainExactlyInAnyOrder` 불일치, 빈 리스트).

- [ ] **Step 3: 서비스에 포트 주입·호출**

`SendTeamInterestService.kt` 수정:

(a) import 추가:
```kotlin
import com.org.oneulsogae.core.match.command.application.port.out.SaveRecommendedTeamHistoryPort
```

(b) 생성자에 의존성 추가(`saveTeamMatchPort` 다음 줄):
```kotlin
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val saveRecommendedTeamHistoryPort: SaveRecommendedTeamHistoryPort,
	private val getTeamPort: GetTeamPort,
```

(c) `completeMatch`에서 `saveChatRoomUseCase.save(...)` 호출 직후, 알림 발행 `teams.values.forEach` 직전에 추가:
```kotlin
		// 성사 이력 기록: 양 팀 구성원 ↔ 상대 팀. 추천 배치가 이미 매칭한 상대를 다시 추천하지 않도록 한다. (성사와 같은 트랜잭션)
		saveRecommendedTeamHistoryPort.saveAll(teams.matchHistories())
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendTeamInterestE2ETest"`
Expected: PASS (MATCHED 케이스에서 4행 기록 확인, 기존 케이스 전부 유지)

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/SendTeamInterestService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendTeamInterestE2ETest.kt
git commit -m "feat(match): 팀 매칭 성사 시 재매칭 제외 이력 기록"
```

---

### Task 4: 조회 dao + 배치 통합 + 유닛 테스트

배치가 유저별 매칭 이력을 단건 seek로 조회해 후보에서 제외하도록 조회 dao와 배치 통합을 추가한다.

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetRecommendedTeamHistoryDao.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetRecommendedTeamHistoryDaoImpl.kt`
- Modify: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/RecommendedTeamBatchService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/RecommendedTeamBatchServiceTest.kt`

**Interfaces:**
- Consumes: `RecommendedTeamHistoryJpaRepository` (Task 2)
- Produces: `interface GetRecommendedTeamHistoryDao { fun findMatchedTeamIds(userId: Long): Set<Long> }` + `@Component` 구현

- [ ] **Step 1: 배치 유닛 테스트 작성 (실패)**

`RecommendedTeamBatchServiceTest.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.match.command.application.RecommendedTeamBatchService
import com.org.oneulsogae.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.RegionShuffler
import com.org.oneulsogae.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.match.command.domain.RecommendedTeamBatchResult
import com.org.oneulsogae.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendedTeamHistoryDao
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendedTeamRecordDao
import com.org.oneulsogae.scheduler.match.query.dto.CandidateTeam
import com.org.oneulsogae.scheduler.match.query.dto.RecommendableSoloUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

class RecommendedTeamBatchServiceTest : DescribeSpec({

    val fixedNow: LocalDateTime = LocalDateTime.of(2026, 6, 27, 9, 0)

    // 단일 대상·고정 후보로 배치를 구성한다. matchedTeamIds는 유저별 과거 매칭 상대. saves에 replace 호출이 기록된다.
    fun service(
        target: RecommendableSoloUser,
        candidates: List<CandidateTeam>,
        matchedTeamIds: Set<Long>,
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
            getRecommendedTeamHistoryDao = object : GetRecommendedTeamHistoryDao {
                override fun findMatchedTeamIds(userId: Long): Set<Long> = matchedTeamIds
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

        it("과거 매칭한 상대 팀은 추천하지 않고 같은 권역의 다른 팀을 고른다") {
            val saves: MutableList<Triple<Long, Long, LocalDate>> = mutableListOf()
            val candidates: List<CandidateTeam> = listOf(
                CandidateTeam(teamId = 100L, gender = Gender.FEMALE, regionId = 1L),
                CandidateTeam(teamId = 200L, gender = Gender.FEMALE, regionId = 1L),
            )

            service(target, candidates, matchedTeamIds = setOf(100L), saves).run()

            saves shouldContainExactly listOf(Triple(1L, 200L, LocalDate.of(2026, 6, 27)))
        }

        it("권역의 모든 후보가 과거 매칭 상대면 추천하지 않는다(skipped)") {
            val saves: MutableList<Triple<Long, Long, LocalDate>> = mutableListOf()
            val candidates: List<CandidateTeam> = listOf(CandidateTeam(teamId = 100L, gender = Gender.FEMALE, regionId = 1L))

            val result: RecommendedTeamBatchResult = service(target, candidates, matchedTeamIds = setOf(100L), saves).run()

            saves shouldBe emptyList()
            result.recommended shouldBe 0
            result.skipped shouldBe 1
        }
    }
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.RecommendedTeamBatchServiceTest"`
Expected: 컴파일 실패(`GetRecommendedTeamHistoryDao` 미정의 + 생성자 인자 불일치).

- [ ] **Step 3: 조회 dao 인터페이스**

`GetRecommendedTeamHistoryDao.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.query.dao

/**
 * 유저가 과거 MATCHED한 상대 팀 조회 dao. (조회 전용) 구현은 infra가 담당한다.
 * 배치가 유저별 단건 seek로 재매칭 상대를 제외하는 데 쓴다.
 */
interface GetRecommendedTeamHistoryDao {

    /** [userId]가 과거 매칭한 상대 team_id 집합. 없으면 빈 집합. */
    fun findMatchedTeamIds(userId: Long): Set<Long>
}
```

- [ ] **Step 4: 조회 dao 구현**

`GetRecommendedTeamHistoryDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.match.query

import com.org.oneulsogae.infra.match.command.entity.RecommendedTeamHistoryEntity
import com.org.oneulsogae.infra.match.command.repository.RecommendedTeamHistoryJpaRepository
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendedTeamHistoryDao
import org.springframework.stereotype.Component

/**
 * scheduler [GetRecommendedTeamHistoryDao]의 구현. user_id로 단건 seek해 매칭한 상대 team_id를 모은다.
 * (recommended_team_histories.ux_user_id_team_id의 선두 컬럼 user_id로 seek)
 */
@Component
class GetRecommendedTeamHistoryDaoImpl(
    private val recommendedTeamHistoryJpaRepository: RecommendedTeamHistoryJpaRepository,
) : GetRecommendedTeamHistoryDao {

    override fun findMatchedTeamIds(userId: Long): Set<Long> =
        recommendedTeamHistoryJpaRepository.findByUserId(userId)
            .map { entity: RecommendedTeamHistoryEntity -> entity.teamId }
            .toSet()
}
```

- [ ] **Step 5: 배치 서비스 통합**

`RecommendedTeamBatchService.kt` 수정:

(a) import 추가:
```kotlin
import com.org.oneulsogae.scheduler.match.query.dao.GetRecommendedTeamHistoryDao
```

(b) 생성자에 의존성 추가(`getCandidateTeamDao` 다음 줄):
```kotlin
    private val getCandidateTeamDao: GetCandidateTeamDao,
    private val getRecommendedTeamHistoryDao: GetRecommendedTeamHistoryDao,
    private val saveRecommendedTeamPort: SaveRecommendedTeamPort,
```

(c) 루프 안 `findNearestRandomTeam` 호출을 제외 집합 조회와 함께 교체. 기존:
```kotlin
                val teamId: Long? = findNearestRandomTeam(target, pool)
```
변경:
```kotlin
                val excludedTeamIds: Set<Long> = getRecommendedTeamHistoryDao.findMatchedTeamIds(target.userId)
                val teamId: Long? = findNearestRandomTeam(target, pool, excludedTeamIds)
```

(d) `findNearestRandomTeam` 시그니처·본문 교체:
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

(e) 클래스 KDoc의 "이전 매칭/추천 이력은 필터링하지 않는다(과거 이력이 있어도 추천)." 문장을 교체:
```kotlin
 * 후보가 없으면 건너뛴다. 각 유저가 과거 매칭(MATCHED)했던 상대 팀은 그 유저에게 다시 추천하지 않는다(유저별 재매칭 제외).
 * 한 사용자의 실패가 다른 사용자에 전파되지 않도록 격리한다.
```

- [ ] **Step 6: 유닛 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.RecommendedTeamBatchServiceTest"`
Expected: PASS

- [ ] **Step 7: 전체 빌드 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (스프링 컨텍스트가 새 dao/adapter 빈으로 배치·성사 서비스를 주입)

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dao/GetRecommendedTeamHistoryDao.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetRecommendedTeamHistoryDaoImpl.kt \
        oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/RecommendedTeamBatchService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/RecommendedTeamBatchServiceTest.kt
git commit -m "feat(match): 추천 팀 배치에 유저별 재매칭 팀 제외 적용"
```

---

## 범위 밖 (명시)

- 백필 없음(테이블 도입 이후 매칭만 추적).
- 기존 `recommended_teams` 행은 손대지 않음(유저당 upsert로 다음 실행 때 갱신).
- 전역적 "성사된 팀은 누구에게도 추천 안 함"은 범위 아님.

## Self-Review

- **스펙 커버리지**: 테이블·엔티티·어댑터(Task 2), 도메인 캡슐화 `matchHistories`·SavePort(Task 1), 성사 시 같은 트랜잭션 기록(Task 3), 유저별 seek 조회·배치 제외(Task 4). 멱등·중복키, 테스트 4종 모두 대응.
- **Placeholder 스캔**: 모든 코드 블록 실제 구현. TODO/TBD 없음.
- **타입 일관성**: `RecommendedTeamHistory(userId, teamId)`, `Teams.matchHistories(): List<RecommendedTeamHistory>`, `SaveRecommendedTeamHistoryPort.saveAll(List<RecommendedTeamHistory>)`, `GetRecommendedTeamHistoryDao.findMatchedTeamIds(Long): Set<Long>`, `findNearestRandomTeam(target, pool, Set<Long>)` 전 태스크 일관. 배치 생성자 인자 순서(`getRecommendedTeamHistoryDao`는 `getCandidateTeamDao`와 `saveRecommendedTeamPort` 사이)가 Task 4 본문과 유닛 테스트 픽스처에서 일치.
