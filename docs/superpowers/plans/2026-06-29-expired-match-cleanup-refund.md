# 만료 매칭 soft-delete + 코인 환불 배치 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 만료된(미성사) 솔로·팀 매칭 데이터를 매시간 soft-delete 하고, 한쪽만 신청(APPLY)한 매칭의 신청자에게 신청 비용의 절반을 환불하며 환불 안내 팝업을 띄운다.

**Architecture:** 매치별 soft-delete + 코인 환불 + 팝업 생성은 도메인 교차 조율이라 **core**의 `ExpireMatchService`(매치 1건 = `@Transactional` 1개)가 담당한다. **scheduler** 모듈은 만료 id 목록을 받아 매치별로 호출하는 루프(기존 배치 컨벤션)만 갖고, **infra** 어댑터가 만료 조회(QueryDSL)와 core 위임(브리지)을 구현한다. **api**가 `@Scheduled`로 트리거한다. 팀 환불 대상을 식별하기 위해 `matched_teams`에 `applicant_user_id`를 신규 기록한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4, Spring Data JPA + QueryDSL, MySQL, Kotest(도메인 유닛 / E2E Testcontainers).

## Global Constraints

- 응답·주석·커밋 메시지는 한국어. `meeple-backend`만 수정(프론트엔드 변경은 안내만).
- 타입 명시: 변수·반환·람다 파라미터 타입을 생략하지 않는다(표현식 본문 포함).
- 현재 시각은 `TimeGenerator`로 주입받아 `now()`로 얻고 도메인엔 파라미터로 넘긴다. `LocalDateTime.now()` 직접 호출 금지(픽스처 제외).
- 다른 도메인의 데이터·동작은 그 도메인의 in-port `UseCase`로만 호출한다(out-port·Service 구현체 직접 주입 금지).
- 조회(`Get…`)와 명령(`Save…`)을 한 포트에 섞지 않는다. 조회 경로는 부수효과 없음.
- 일급 컬렉션은 `values`를 서비스에서 직접 들추지 말고 컬렉션 메서드로 캡슐화.
- 쿼리는 인덱스 seek를 고려한다(동등 조건 → 정렬/범위 컬럼 순 복합 인덱스).
- 솔로 신청 비용 `DATING_INIT`=32(환불 16), 팀 신청 비용 `MEETING_INIT`=40(환불 20). 환불은 신청 비용/2 (내림).
- 환불 적립 유형은 기존 `CoinGetType.REFUND`("환불"), 코인 적립은 `AcquireCoinUseCase`.

---

### Task 1: 미팅 환불 팝업 타입·팩토리·유스케이스

솔로 환불 팝업(`MATCH_FAILED_REFUND`)은 이미 존재한다. 팀(미팅)용을 같은 패턴으로 추가한다.

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/popup/PopupType.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/popup/command/domain/Popup.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/popup/command/application/port/in/CreateRefundPopupUseCase.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/popup/command/application/CreateRefundPopupService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/popup/PopupTest.kt` (신규)

**Interfaces:**
- Produces: `PopupType.MEETING_FAILED_REFUND`; `Popup.meetingFailedRefund(userId: Long, refundAmount: Int, now: LocalDateTime): Popup`; `CreateRefundPopupUseCase.createMeetingFailedRefund(userId: Long, refundAmount: Int)`.

- [ ] **Step 1: 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/popup/PopupTest.kt` 신규:

```kotlin
package com.org.oneulsogae.domain.popup

import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.core.popup.command.domain.Popup
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [Popup] 도메인 팩토리 유닛 테스트. (환불 안내 팝업 생성)
 */
class PopupTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 29, 12, 0)

	describe("meetingFailedRefund") {
		it("미팅 환불 안내 개인 팝업을 생성한다 (MEETING_FAILED_REFUND, 7일 노출)") {
			val popup: Popup = Popup.meetingFailedRefund(userId = 100L, refundAmount = 20, now = now)

			popup.popUpType shouldBe PopupType.MEETING_FAILED_REFUND
			popup.userId shouldBe 100L
			popup.description shouldBe "미팅이 매칭되지 않아 사용한 코인의 절반인 20코인을 돌려드렸어요."
			popup.exposedFrom shouldBe now
			popup.exposedTo shouldBe now.plusDays(7)
		}
	}
})
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `Unresolved reference: meetingFailedRefund`, `MEETING_FAILED_REFUND`

- [ ] **Step 3: PopupType에 enum 추가**

`PopupType.kt`의 `NEW_USER` 앞(또는 `MATCH_FAILED_REFUND` 다음)에:

```kotlin
	/** 미팅(팀) 매칭 실패로 사용한 코인의 절반을 환불할 때 보여주는 팝업. */
	MEETING_FAILED_REFUND("미팅 매칭 실패 환불"),
```

- [ ] **Step 4: Popup.meetingFailedRefund 팩토리 추가**

`Popup.kt`의 `companion object` 안, `matchFailedRefund` 다음에:

```kotlin
		/**
		 * 미팅(팀) 매칭 실패로 [refundAmount]코인을 환불한 사실을 알리는 개인([userId]) 팝업을 만든다.
		 * [now]부터 [REFUND_POPUP_EXPOSURE_DAYS]일 동안만 노출한다. (소개팅 [matchFailedRefund]와 동일 골격, 문구만 미팅 기준)
		 */
		fun meetingFailedRefund(userId: Long, refundAmount: Int, now: LocalDateTime): Popup =
			Popup(
				title = "미팅 매칭 실패 환불",
				description = "미팅이 매칭되지 않아 사용한 코인의 절반인 ${refundAmount}코인을 돌려드렸어요.",
				displayOrder = 0,
				buttonText = "확인",
				popUpType = PopupType.MEETING_FAILED_REFUND,
				userId = userId,
				exposedFrom = now,
				exposedTo = now.plusDays(REFUND_POPUP_EXPOSURE_DAYS),
			)
```

- [ ] **Step 5: UseCase·Service에 createMeetingFailedRefund 추가**

`CreateRefundPopupUseCase.kt`에:

```kotlin
	/** [userId]에게 [refundAmount]코인 환불을 알리는 미팅(팀) 매칭 실패 개인 팝업을 생성한다. */
	fun createMeetingFailedRefund(userId: Long, refundAmount: Int)
```

`CreateRefundPopupService.kt`의 `createMatchFailedRefund` 다음에:

```kotlin
	override fun createMeetingFailedRefund(userId: Long, refundAmount: Int) {
		val now: LocalDateTime = timeGenerator.now()
		savePopupPort.save(Popup.meetingFailedRefund(userId = userId, refundAmount = refundAmount, now = now))
	}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.popup.PopupTest"`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-common oneulsogae-core oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/popup/PopupTest.kt
git commit -m "feat(popup): 미팅 매칭 실패 환불 팝업 타입·생성 추가"
```

---

### Task 2: 팀 지불자 추적(applicant_user_id) + 환불 산정 도메인

`matched_teams`에 신청자 userId를 도메인에 싣고, `TeamMatch.failureRefunds()`로 환불 대상을 산정한다. `apply`/`respond` 시그니처가 바뀌므로 기존 호출부·테스트도 함께 갱신한다.

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeam.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeams.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMatch.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/SendTeamInterestService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamTest.kt` (갱신)
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt` (갱신)
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMatchTest.kt` (갱신 + failureRefunds 추가)

**Interfaces:**
- Consumes: `MatchRefund(userId: Long, amount: Int)` (기존, `com.org.oneulsogae.core.match.command.domain`).
- Produces: `MatchedTeam.applicantUserId: Long?`; `MatchedTeam.apply(applicantUserId: Long): MatchedTeam`; `MatchedTeams.apply(teamId: Long, applicantUserId: Long): MatchedTeams`; `MatchedTeams.applied(): List<MatchedTeam>`; `TeamMatch.respond(teamId: Long, applicantUserId: Long): TeamMatch`; `TeamMatch.failureRefunds(): List<MatchRefund>`.

- [ ] **Step 1: 실패 테스트 작성 (failureRefunds + applied + applicantUserId 전파)**

`TeamMatchTest.kt`에 `describe` 블록 추가(파일 끝 `})` 앞):

```kotlin
	describe("failureRefunds - 미성사 만료 환불 산정") {
		it("신청(APPLY)한 팀의 지불자에게만 신청 비용의 절반을 환불한다") {
			// teamA(10) 지불자 userId=100이 신청, teamB(20)는 미신청
			val partiallyAccepted: TeamMatch =
				TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L, 100L)

			val refunds: List<com.org.oneulsogae.core.match.command.domain.MatchRefund> = partiallyAccepted.failureRefunds()

			refunds.size shouldBe 1
			refunds.first().userId shouldBe 100L
			refunds.first().amount shouldBe 20 // MEETING_INIT(40)의 절반
		}

		it("아무도 신청하지 않았으면 환불 대상이 없다") {
			val proposed: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			proposed.failureRefunds() shouldBe emptyList()
		}
	}
```

`MatchedTeamsTest.kt`의 기존 `matchedTeams.apply(10L)` 호출을 `matchedTeams.apply(10L, 100L)`로, `none.apply(10L)`/`partial.apply(20L)`를 `none.apply(10L, 100L)`/`partial.apply(20L, 200L)`로 바꾸고, `applied` 테스트를 추가:

```kotlin
	describe("applied - 신청한 팀") {
		it("APPLY/ACTIVE인 팀만 돌려준다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).apply(10L, 100L)

			val applied: List<com.org.oneulsogae.core.match.command.domain.MatchedTeam> = matchedTeams.applied()

			applied.map { it.teamId } shouldBe listOf(10L)
			applied.first().applicantUserId shouldBe 100L
		}
	}
```

`MatchedTeamTest.kt`의 `waitingTeam().apply()`(2곳: "apply", "activate" describe)를 `waitingTeam().apply(100L)`로 바꾸고, apply 테스트에 신청자 기록 검증 추가:

```kotlin
	describe("apply") {
		it("이 팀을 신청(APPLY) 상태로 전이하고 신청자를 기록한다") {
			val applied: MatchedTeam = waitingTeam().apply(100L)

			applied.status shouldBe MatchedTeamStatus.APPLY
			applied.applicantUserId shouldBe 100L
		}
	}
```

(필요 import: `TeamMatchType`는 TeamMatchTest에 이미 있음. `now`도 이미 정의됨.)

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `apply`/`respond`/`applied`/`failureRefunds`/`applicantUserId` 미해결.

- [ ] **Step 3: MatchedTeam에 applicantUserId + apply(applicantUserId) 반영**

`MatchedTeam.kt` — 필드 추가(`status` 다음):

```kotlin
	val status: MatchedTeamStatus = MatchedTeamStatus.WAITING,
	/** 이 팀이 신청(APPLY)할 때 코인을 지불한 구성원 userId. 미신청이면 null. (미성사 만료 환불 대상 식별용) */
	val applicantUserId: Long? = null,
	val deletedAt: LocalDateTime? = null,
```

`apply()` 시그니처 변경:

```kotlin
	/** 이 팀이 매칭을 신청한(APPLY) 새 모델을 반환한다. 코인을 지불한 [applicantUserId]를 함께 기록한다. */
	fun apply(applicantUserId: Long): MatchedTeam =
		copy(status = MatchedTeamStatus.APPLY, applicantUserId = applicantUserId)
```

- [ ] **Step 4: MatchedTeams에 apply(teamId, applicantUserId) + applied()**

`MatchedTeams.kt` — `apply` 시그니처 변경:

```kotlin
	/** [teamId] 팀을 신청(APPLY) 처리하고 지불자([applicantUserId])를 기록한 새 컬렉션을 반환한다. (나머지는 그대로) */
	fun apply(teamId: Long, applicantUserId: Long): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> if (matchedTeam.teamId == teamId) matchedTeam.apply(applicantUserId) else matchedTeam })
```

`applied()` 추가(`anyApplied` 다음):

```kotlin
	/** 신청(APPLY/ACTIVE)한 팀들. (미성사 만료 환불 대상 산정에 쓴다) */
	fun applied(): List<MatchedTeam> =
		values.filter { matchedTeam: MatchedTeam -> matchedTeam.hasApplied }
```

- [ ] **Step 5: TeamMatch에 respond(teamId, applicantUserId) + failureRefunds()**

`TeamMatch.kt` — `respond` 시그니처 변경:

```kotlin
	fun respond(teamId: Long, applicantUserId: Long): TeamMatch {
		val applied: TeamMatch = copy(matchedTeams = matchedTeams.apply(teamId, applicantUserId))
		val recomputed: TeamMatch = applied.withRecomputedStatus()
		return if (recomputed.status == MatchStatus.MATCHED) recomputed.extendExpirationForMatched() else recomputed
	}
```

`failureRefunds()` 추가(`memberKey()` 근처):

```kotlin
	/**
	 * 미성사(만료) 제거 시, 신청한 팀의 지불자별 환불 금액 목록을 산정한다. (1:1 [Match.failureRefunds] 미러)
	 * 실제로 코인을 지불한(신청한) 팀의 [MatchedTeam.applicantUserId]에게만 신청 비용([dateInitAmount])의 절반(내림)을 돌려준다.
	 */
	fun failureRefunds(): List<MatchRefund> =
		matchedTeams.applied()
			.mapNotNull { team: MatchedTeam -> team.applicantUserId?.let { userId: Long -> MatchRefund(userId = userId, amount = dateInitAmount / 2) } }
			.filter { refund: MatchRefund -> refund.amount > 0 }
```

`TeamMatch.kt` 상단 import에 `import com.org.oneulsogae.core.match.command.domain.MatchedTeam`는 같은 패키지라 불필요. `MatchRefund`도 같은 패키지라 불필요.

- [ ] **Step 6: 호출부 갱신 (SendTeamInterestService)**

`SendTeamInterestService.kt:68`:

```kotlin
		val updated: TeamMatch = saveTeamMatchPort.save(teamMatch.respond(actorTeam.id, userId))
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMatchTest" --tests "com.org.oneulsogae.domain.match.MatchedTeamTest" --tests "com.org.oneulsogae.domain.match.MatchedTeamsTest"`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-core oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match
git commit -m "feat(match): 팀 매칭 신청자(applicant) 기록 및 미성사 환불 산정 추가"
```

---

### Task 3: matched_teams 영속성에 applicant_user_id 반영 + 마이그레이션

도메인의 `applicantUserId`를 DB까지 잇는다.

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/MatchedTeamEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/mapper/MatchedTeamMapper.kt`
- Create: `docs/migration/matched_teams_applicant_user_id.sql`

**Interfaces:**
- Consumes: `MatchedTeam.applicantUserId: Long?` (Task 2).
- Produces: `MatchedTeamEntity(teamMatchId, teamId, status, applicantUserId)` 생성자(추가 파라미터 기본값 null); 엔티티 `applicant_user_id` 컬럼.

- [ ] **Step 1: 엔티티에 컬럼 추가**

`MatchedTeamEntity.kt`의 `status` 프로퍼티 다음에:

```kotlin
	/** 이 팀이 신청(APPLY)할 때 코인을 지불한 구성원 userId. 미신청이면 null. (미성사 만료 환불 대상 식별용) */
	@Column(name = "applicant_user_id")
	var applicantUserId: Long? = null,
```

- [ ] **Step 2: 매퍼 양방향 매핑**

`MatchedTeamMapper.kt` — `toDomain()`에 `applicantUserId = applicantUserId,` 추가, `toEntity()`에 생성자 인자 `applicantUserId = applicantUserId,` 추가:

```kotlin
fun MatchedTeamEntity.toDomain(): MatchedTeam =
	MatchedTeam(
		id = id ?: 0,
		teamMatchId = teamMatchId,
		teamId = teamId,
		status = status,
		applicantUserId = applicantUserId,
		deletedAt = deletedAt,
	)

fun MatchedTeam.toEntity(): MatchedTeamEntity =
	MatchedTeamEntity(
		teamMatchId = teamMatchId,
		teamId = teamId,
		status = status,
		applicantUserId = applicantUserId,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
```

- [ ] **Step 3: 마이그레이션 SQL 작성**

`docs/migration/matched_teams_applicant_user_id.sql` 신규:

```sql
-- matched_teams: 팀이 신청(APPLY)할 때 코인을 지불한 구성원 userId를 기록한다.
--   미성사 만료 시 이 컬럼으로 환불 대상(지불자)을 식별한다. (기존 행은 NULL → 소급 환불 대상 아님)
ALTER TABLE matched_teams
    ADD COLUMN applicant_user_id BIGINT NULL;
```

- [ ] **Step 4: 컴파일 + 기존 팀 매칭 E2E 회귀 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin && ./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendTeamInterestE2ETest"`
Expected: PASS (신청 시 applicant_user_id가 채워져도 기존 시나리오 그대로 통과)

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-infra docs/migration/matched_teams_applicant_user_id.sql
git commit -m "feat(match): matched_teams에 applicant_user_id 컬럼·매핑 추가"
```

---

### Task 4: core ExpireMatchService — 매치별 만료 처리(soft-delete + 환불 + 팝업)

매치 1건을 한 트랜잭션으로 처리한다. soft-delete가 같은 tx에서 커밋돼 다음 실행 시 재환불되지 않는다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/ExpireMatchUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/ExpireMatchService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/ExpireMatchServiceIntegrationTest.kt` (신규)

**Interfaces:**
- Consumes: `GetMatchPort.findById(id: Long): Match?`, `SaveMatchPort.save(match: Match): Match`, `GetTeamMatchPort.findById(teamMatchId: Long): TeamMatch?`, `SaveTeamMatchPort.save(teamMatch: TeamMatch): TeamMatch`, `Match.failureRefunds()`, `Match.delete(now)`, `TeamMatch.failureRefunds()` (Task 2), `TeamMatch.delete(now)`, `AcquireCoinUseCase.acquire(userId, AcquireCoinCommand(amount, CoinGetType.REFUND))`, `CreateRefundPopupUseCase.createMatchFailedRefund(userId, amount)` / `createMeetingFailedRefund(userId, amount)` (Task 1), core `TimeGenerator.now()`.
- Produces: `ExpireMatchUseCase { fun expireSoloMatch(matchId: Long); fun expireTeamMatch(teamMatchId: Long) }`.

- [ ] **Step 1: 실패 테스트 작성 (통합 — 유스케이스 직접 호출)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/ExpireMatchServiceIntegrationTest.kt` 신규:

```kotlin
package com.org.oneulsogae.api.scheduler

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.match.command.application.port.`in`.ExpireMatchUseCase
import com.org.oneulsogae.core.match.command.domain.MatchedTeams
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.SoloMatchEntityFixture
import com.org.oneulsogae.infra.fixture.SoloMatchMemberEntityFixture
import com.org.oneulsogae.infra.match.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QMatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMatchEntity
import com.org.oneulsogae.infra.match.command.entity.SoloMatchEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMatchEntity
import com.org.oneulsogae.infra.popup.command.entity.QPopupEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [ExpireMatchUseCase](ExpireMatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 매치 1건의 soft-delete + 코인 환불 + 팝업 생성을 직접 검증한다.
 */
class ExpireMatchServiceIntegrationTest(
	private val expireMatchUseCase: ExpireMatchUseCase,
) : AbstractIntegrationSupport({

	describe("expireSoloMatch") {
		context("한쪽만 신청(APPLY)한 PARTIALLY_ACCEPTED 솔로 매칭은") {
			it("soft-delete하고 신청자에게 16코인 환불 + 소개팅 환불 팝업을 만든다") {
				val applicantId = 1001L
				val partnerId = 2001L
				val header: SoloMatchEntity = IntegrationUtil.persist(
					SoloMatchEntityFixture.create(memberKey = "1001-2001", status = MatchStatus.PARTIALLY_ACCEPTED),
				)
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = applicantId, gender = Gender.MALE, status = MatchMemberStatus.APPLY))
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = partnerId, gender = Gender.FEMALE, status = MatchMemberStatus.WAITING))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = applicantId, balance = 100))

				expireMatchUseCase.expireSoloMatch(header.id!!)

				soloMatchById(header.id!!).shouldBeNull()
				coinBalanceOf(applicantId) shouldBe 116
				popupExists(applicantId, PopupType.MATCH_FAILED_REFUND) shouldBe true
			}
		}

		context("아무도 신청하지 않은 PROPOSED 솔로 매칭은") {
			it("soft-delete만 하고 환불·팝업이 없다") {
				val a = 1101L
				val b = 2101L
				val header: SoloMatchEntity = IntegrationUtil.persist(
					SoloMatchEntityFixture.create(memberKey = "1101-2101", status = MatchStatus.PROPOSED),
				)
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = a, gender = Gender.MALE, status = MatchMemberStatus.WAITING))
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = b, gender = Gender.FEMALE, status = MatchMemberStatus.WAITING))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = a, balance = 100))

				expireMatchUseCase.expireSoloMatch(header.id!!)

				soloMatchById(header.id!!).shouldBeNull()
				coinBalanceOf(a) shouldBe 100
				popupExists(a, PopupType.MATCH_FAILED_REFUND) shouldBe false
			}
		}
	}

	describe("expireTeamMatch") {
		context("한쪽 팀만 신청(APPLY)한 PARTIALLY_ACCEPTED 팀 매칭은") {
			it("soft-delete하고 지불자에게 20코인 환불 + 미팅 환불 팝업을 만든다") {
				val applicantId = 3001L
				val teamAId = 10L
				val teamBId = 20L
				val header: TeamMatchEntity = IntegrationUtil.persist(
					TeamMatchEntity(
						memberKey = MatchedTeams.of(listOf(teamAId, teamBId)).memberKey(),
						introducedDate = LocalDate.now(),
						expiresAt = LocalDateTime.now().minusHours(1),
						status = MatchStatus.PARTIALLY_ACCEPTED,
						matchType = TeamMatchType.DAILY,
						dateInitAmount = 40,
						dateAcceptAmount = 40,
					),
				)
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamAId, status = MatchedTeamStatus.APPLY, applicantUserId = applicantId))
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamBId, status = MatchedTeamStatus.WAITING))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = applicantId, balance = 100))

				expireMatchUseCase.expireTeamMatch(header.id!!)

				teamMatchById(header.id!!).shouldBeNull()
				coinBalanceOf(applicantId) shouldBe 120
				popupExists(applicantId, PopupType.MEETING_FAILED_REFUND) shouldBe true
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
	}
})

private fun soloMatchById(id: Long): SoloMatchEntity? {
	val q = QSoloMatchEntity.soloMatchEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.id.eq(id)).fetchOne()
}

private fun teamMatchById(id: Long): TeamMatchEntity? {
	val q = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.id.eq(id)).fetchOne()
}

private fun coinBalanceOf(userId: Long): Int {
	val q = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery().select(q.balance).from(q).where(q.userId.eq(userId)).fetchOne()!!
}

private fun popupExists(userId: Long, type: PopupType): Boolean {
	val q = QPopupEntity.popupEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.userId.eq(userId).and(q.popUpType.eq(type))).fetchFirst() != null
}
```

> 참고: `SoloMatchEntityFixture.create`의 기본 `expiresAt`은 `now().plusDays(1)`이지만, 이 테스트는 유스케이스를 직접 호출하므로 만료 시각과 무관하게 동작한다(만료 필터는 Task 6 쿼리에서). 헤더 상태만 의미가 있다.

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `Unresolved reference: ExpireMatchUseCase`

- [ ] **Step 3: in-port 작성**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/ExpireMatchUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application.port.`in`

/**
 * 만료된(미성사) 매칭 1건을 정리하는 유스케이스(in-port). (매치 1건 = 트랜잭션 1개)
 * 매칭을 soft-delete하고, 한쪽만 신청(APPLY)해 성사되지 못한 경우 신청자에게 신청 비용의 절반을 환불하며 환불 팝업을 만든다.
 * 만료 대상 선별(성사·종료·미만료 제외)은 호출 측(배치 조회)이 책임진다.
 */
interface ExpireMatchUseCase {

	/** 만료된 솔로 매칭([matchId])을 정리한다. (없으면 무시) */
	fun expireSoloMatch(matchId: Long)

	/** 만료된 팀 매칭([teamMatchId])을 정리한다. (없으면 무시) */
	fun expireTeamMatch(teamMatchId: Long)
}
```

- [ ] **Step 4: 서비스 작성**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/ExpireMatchService.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.match.command.application.port.`in`.ExpireMatchUseCase
import com.org.oneulsogae.core.match.command.application.port.out.GetMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.match.command.domain.Match
import com.org.oneulsogae.core.match.command.domain.MatchRefund
import com.org.oneulsogae.core.match.command.domain.TeamMatch
import com.org.oneulsogae.core.popup.command.application.port.`in`.CreateRefundPopupUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [ExpireMatchUseCase] 구현. 만료된(미성사) 매칭 1건을 한 트랜잭션으로 정리한다.
 * soft-delete와 코인 환불·팝업이 같은 트랜잭션에서 커밋되므로, 다음 실행 시 같은 매칭이 다시 조회·환불되지 않는다.
 * 코인 적립([AcquireCoinUseCase])·팝업 생성([CreateRefundPopupUseCase])은 다른 도메인 in-port로만 호출한다.
 */
@Service
class ExpireMatchService(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val createRefundPopupUseCase: CreateRefundPopupUseCase,
	private val timeGenerator: TimeGenerator,
) : ExpireMatchUseCase {

	@Transactional
	override fun expireSoloMatch(matchId: Long) {
		val match: Match = getMatchPort.findById(matchId) ?: return
		val now: LocalDateTime = timeGenerator.now()
		val refunds: List<MatchRefund> = match.failureRefunds()
		saveMatchPort.save(match.delete(now))
		refunds.forEach { refund: MatchRefund ->
			acquireCoinUseCase.acquire(refund.userId, AcquireCoinCommand(amount = refund.amount, coinType = CoinGetType.REFUND))
			createRefundPopupUseCase.createMatchFailedRefund(refund.userId, refund.amount)
		}
	}

	@Transactional
	override fun expireTeamMatch(teamMatchId: Long) {
		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId) ?: return
		val now: LocalDateTime = timeGenerator.now()
		val refunds: List<MatchRefund> = teamMatch.failureRefunds()
		saveTeamMatchPort.save(teamMatch.delete(now))
		refunds.forEach { refund: MatchRefund ->
			acquireCoinUseCase.acquire(refund.userId, AcquireCoinCommand(amount = refund.amount, coinType = CoinGetType.REFUND))
			createRefundPopupUseCase.createMeetingFailedRefund(refund.userId, refund.amount)
		}
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.ExpireMatchServiceIntegrationTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/ExpireMatchServiceIntegrationTest.kt
git commit -m "feat(match): 만료 매칭 soft-delete + 코인 환불·팝업 처리 서비스 추가"
```

---

### Task 5: scheduler 모듈 — 배치 잡 + 포트 + 루프 서비스

만료 id 목록을 받아 매치별로 core 처리를 호출하는 루프(건별 격리). 기존 배치 골격을 따른다.

**Files:**
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/domain/ExpireMatchBatchResult.kt`
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/port/in/RunExpireMatchBatchUseCase.kt`
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/port/out/GetExpiredMatchPort.kt`
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/port/out/ExpireMatchPort.kt`
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/application/ExpireMatchBatchService.kt`
- Create: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/command/adapter/ExpireMatchBatchJob.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/ExpireMatchBatchServiceTest.kt` (신규, 페이크 포트 유닛 테스트)

**Interfaces:**
- Consumes: scheduler `TimeGenerator.now()` (`com.org.oneulsogae.scheduler.match.command.application.port.out.TimeGenerator`).
- Produces: `ExpireMatchBatchResult(soloExpired: Int, teamExpired: Int, soloFailed: Int, teamFailed: Int)`; `RunExpireMatchBatchUseCase.run(): ExpireMatchBatchResult`; `GetExpiredMatchPort { fun findExpiredSoloMatchIds(now: LocalDateTime): List<Long>; fun findExpiredTeamMatchIds(now: LocalDateTime): List<Long> }`; `ExpireMatchPort { fun expireSoloMatch(matchId: Long); fun expireTeamMatch(teamMatchId: Long) }`; `ExpireMatchBatchJob.run(): ExpireMatchBatchResult?`.

- [ ] **Step 1: 실패 테스트 작성 (루프·격리·집계)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/ExpireMatchBatchServiceTest.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match

import com.org.oneulsogae.scheduler.match.command.application.ExpireMatchBatchService
import com.org.oneulsogae.scheduler.match.command.application.port.out.ExpireMatchPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.GetExpiredMatchPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.match.command.domain.ExpireMatchBatchResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ExpireMatchBatchService] 유닛 테스트. 페이크 포트로 루프·건별 격리·집계를 검증한다.
 */
class ExpireMatchBatchServiceTest : DescribeSpec({

	val fixedNow: LocalDateTime = LocalDateTime.of(2026, 6, 29, 12, 0)
	val timeGenerator = object : TimeGenerator {
		override fun now(): LocalDateTime = fixedNow
	}

	describe("run") {
		it("만료 솔로·팀을 건별로 처리하고, 한 건이 실패해도 나머지를 처리하며 실패를 집계한다") {
			val getExpired = object : GetExpiredMatchPort {
				override fun findExpiredSoloMatchIds(now: LocalDateTime): List<Long> = listOf(1L, 2L)
				override fun findExpiredTeamMatchIds(now: LocalDateTime): List<Long> = listOf(3L)
			}
			val processedSolo = mutableListOf<Long>()
			val processedTeam = mutableListOf<Long>()
			val expire = object : ExpireMatchPort {
				override fun expireSoloMatch(matchId: Long) {
					if (matchId == 2L) throw RuntimeException("boom")
					processedSolo.add(matchId)
				}
				override fun expireTeamMatch(teamMatchId: Long) {
					processedTeam.add(teamMatchId)
				}
			}
			val service = ExpireMatchBatchService(getExpired, expire, timeGenerator)

			val result: ExpireMatchBatchResult = service.run()

			result.soloExpired shouldBe 1
			result.soloFailed shouldBe 1
			result.teamExpired shouldBe 1
			result.teamFailed shouldBe 0
			processedSolo shouldBe listOf(1L)
			processedTeam shouldBe listOf(3L)
		}
	}
})
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — 미해결 참조들.

- [ ] **Step 3: 결과 모델 작성**

`ExpireMatchBatchResult.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.command.domain

/**
 * 만료 매칭 정리 배치 실행 결과 요약. (정리된 솔로/팀 수, 건별 실패 수)
 */
data class ExpireMatchBatchResult(
	val soloExpired: Int,
	val teamExpired: Int,
	val soloFailed: Int,
	val teamFailed: Int,
)
```

- [ ] **Step 4: in-port·out-port 작성**

`RunExpireMatchBatchUseCase.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.command.application.port.`in`

import com.org.oneulsogae.scheduler.match.command.domain.ExpireMatchBatchResult

/**
 * 만료 매칭 정리 배치 실행 유스케이스(in-port). 만료된(미성사) 솔로·팀 매칭을 soft-delete하고 신청자에게 절반 환불한다.
 * 구현은 [com.org.oneulsogae.scheduler.match.command.application.ExpireMatchBatchService].
 */
interface RunExpireMatchBatchUseCase {

	fun run(): ExpireMatchBatchResult
}
```

`GetExpiredMatchPort.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.command.application.port.out

import java.time.LocalDateTime

/**
 * 만료된(미성사) 매칭 id를 조회하는 아웃포트. (조회 전용)
 * 만료 = soft-delete 안 됨 + [now] 기준 만료 시각 경과 + 성사/종료가 아닌(PROPOSED/PARTIALLY_ACCEPTED) 상태.
 * 실제 구현(엔티티 조회)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface GetExpiredMatchPort {

	/** 만료된 솔로 매칭 id 목록. */
	fun findExpiredSoloMatchIds(now: LocalDateTime): List<Long>

	/** 만료된 팀 매칭 id 목록. */
	fun findExpiredTeamMatchIds(now: LocalDateTime): List<Long>
}
```

`ExpireMatchPort.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.command.application.port.out

/**
 * 만료 매칭 1건을 정리(soft-delete + 환불 + 팝업)하는 아웃포트. (매치 1건 = 트랜잭션 1개)
 * 실제 구현은 infra 어댑터가 core의 만료 처리 유스케이스에 위임한다. (scheduler는 core에 의존하지 않는다)
 */
interface ExpireMatchPort {

	/** 만료된 솔로 매칭([matchId])을 정리한다. */
	fun expireSoloMatch(matchId: Long)

	/** 만료된 팀 매칭([teamMatchId])을 정리한다. */
	fun expireTeamMatch(teamMatchId: Long)
}
```

- [ ] **Step 5: 루프 서비스 작성**

`ExpireMatchBatchService.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.command.application

import com.org.oneulsogae.scheduler.match.command.application.port.`in`.RunExpireMatchBatchUseCase
import com.org.oneulsogae.scheduler.match.command.application.port.out.ExpireMatchPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.GetExpiredMatchPort
import com.org.oneulsogae.scheduler.match.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.match.command.domain.ExpireMatchBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RunExpireMatchBatchUseCase] 구현. 매시간 도는 만료 매칭 정리 배치.
 * 만료된 솔로·팀 매칭 id를 한 번 적재하고, 건별로 [ExpireMatchPort]에 위임한다(매치당 트랜잭션 1개).
 * 한 건의 실패가 다른 건에 전파되지 않도록 건 단위로 격리하고, 예외만 failed로 집계한다.
 */
@Service
class ExpireMatchBatchService(
	private val getExpiredMatchPort: GetExpiredMatchPort,
	private val expireMatchPort: ExpireMatchPort,
	private val timeGenerator: TimeGenerator,
) : RunExpireMatchBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): ExpireMatchBatchResult {
		val now: LocalDateTime = timeGenerator.now()

		var soloExpired = 0
		var soloFailed = 0
		for (matchId: Long in getExpiredMatchPort.findExpiredSoloMatchIds(now)) {
			try {
				expireMatchPort.expireSoloMatch(matchId)
				soloExpired++
			} catch (e: Exception) {
				soloFailed++
				log.warn("만료 솔로 매칭 정리 실패 matchId={}", matchId, e)
			}
		}

		var teamExpired = 0
		var teamFailed = 0
		for (teamMatchId: Long in getExpiredMatchPort.findExpiredTeamMatchIds(now)) {
			try {
				expireMatchPort.expireTeamMatch(teamMatchId)
				teamExpired++
			} catch (e: Exception) {
				teamFailed++
				log.warn("만료 팀 매칭 정리 실패 teamMatchId={}", teamMatchId, e)
			}
		}

		val result: ExpireMatchBatchResult = ExpireMatchBatchResult(soloExpired = soloExpired, teamExpired = teamExpired, soloFailed = soloFailed, teamFailed = teamFailed)
		log.info("만료 매칭 정리 배치 완료: {}", result)
		return result
	}
}
```

- [ ] **Step 6: 배치 잡 작성**

`ExpireMatchBatchJob.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match.command.adapter

import com.org.oneulsogae.scheduler.match.command.application.port.`in`.RunExpireMatchBatchUseCase
import com.org.oneulsogae.scheduler.match.command.domain.ExpireMatchBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 만료 매칭 정리 배치 실행 진입점. (크론 / 관리자 수동 트리거 공통)
 * 크론과 수동 트리거가 모두 이 단일 진입점을 거치므로, 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class ExpireMatchBatchJob(
	private val runExpireMatchBatchUseCase: RunExpireMatchBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	private val running: AtomicBoolean = AtomicBoolean(false)

	/** 만료 매칭 정리 배치를 실행하고 결과를 반환한다. 이미 실행 중이면 건너뛰고 null을 반환한다. */
	fun run(): ExpireMatchBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("만료 매칭 정리 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("만료 매칭 정리 배치 시작")
			val result: ExpireMatchBatchResult = runExpireMatchBatchUseCase.run()
			log.info("만료 매칭 정리 배치 종료: {}", result)
			result
		} finally {
			running.set(false)
		}
	}
}
```

- [ ] **Step 7: 테스트 통과 확인 (컴파일 전체 + 유닛)**

Run: `./gradlew :oneulsogae-scheduler:compileKotlin && ./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.scheduler.match.ExpireMatchBatchServiceTest"`
Expected: PASS

> 주의: 이 시점엔 `GetExpiredMatchPort`·`ExpireMatchPort` 구현체가 없어 `:oneulsogae-api` 전체 컨텍스트 기동은 실패할 수 있다. 위 유닛 테스트는 페이크라 통과한다. 전체 컨텍스트는 Task 6 이후 검증한다.

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-scheduler oneulsogae-api/src/test/kotlin/com/org/oneulsogae/scheduler/match/ExpireMatchBatchServiceTest.kt
git commit -m "feat(scheduler): 만료 매칭 정리 배치 잡·포트·루프 서비스 추가"
```

---

### Task 6: infra 어댑터 — 만료 조회(QueryDSL) + core 위임(브리지)

scheduler 아웃포트 두 개를 infra가 구현한다. 조회는 `@SQLRestriction`이 `deleted_at is null`을 자동 적용하므로 만료 시각·상태만 명시한다.

**Files:**
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetExpiredMatchDaoImpl.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/ExpireMatchBridgeAdapter.kt`

**Interfaces:**
- Consumes: scheduler `GetExpiredMatchPort`, `ExpireMatchPort` (Task 5); core `ExpireMatchUseCase` (Task 4); `JPAQueryFactory`; `QSoloMatchEntity`, `QTeamMatchEntity`.
- Produces: 두 포트의 Spring 빈 구현 → `:oneulsogae-api` 전체 컨텍스트가 기동 가능해짐.

- [ ] **Step 1: 만료 조회 구현 작성**

`GetExpiredMatchDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.match.query

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.infra.match.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMatchEntity
import com.org.oneulsogae.scheduler.match.command.application.port.out.GetExpiredMatchPort
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetExpiredMatchPort] 구현. 만료된(미성사) 매칭 id를 조회한다.
 * 만료 = [now] 기준 expires_at 경과 + 상태가 PROPOSED/PARTIALLY_ACCEPTED. (성사 MATCHED는 만료 시각이 +100년이라 자연 제외)
 * 엔티티 @SQLRestriction("deleted_at is null")이 자동 적용돼 이미 제거된 매칭은 조회되지 않는다.
 * (status 등치 + expires_at 범위를 받치는 (status, expires_at) 복합 인덱스를 둔다 — Task 9)
 */
@Component
class GetExpiredMatchDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetExpiredMatchPort {

	override fun findExpiredSoloMatchIds(now: LocalDateTime): List<Long> {
		val soloMatch: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		return queryFactory
			.select(soloMatch.id)
			.from(soloMatch)
			.where(
				soloMatch.status.`in`(MatchStatus.PROPOSED, MatchStatus.PARTIALLY_ACCEPTED),
				soloMatch.expiresAt.lt(now),
			)
			.fetch()
	}

	override fun findExpiredTeamMatchIds(now: LocalDateTime): List<Long> {
		val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
		return queryFactory
			.select(teamMatch.id)
			.from(teamMatch)
			.where(
				teamMatch.status.`in`(MatchStatus.PROPOSED, MatchStatus.PARTIALLY_ACCEPTED),
				teamMatch.expiresAt.lt(now),
			)
			.fetch()
	}
}
```

- [ ] **Step 2: 브리지 어댑터 작성**

`ExpireMatchBridgeAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.match.command.adapter

import com.org.oneulsogae.core.match.command.application.port.`in`.ExpireMatchUseCase
import com.org.oneulsogae.scheduler.match.command.application.port.out.ExpireMatchPort
import org.springframework.stereotype.Component

/**
 * scheduler [ExpireMatchPort]를 core [ExpireMatchUseCase]에 잇는 브리지 어댑터.
 * scheduler는 core에 의존하지 않으므로(자기 포트만 보유), core의 만료 처리 유스케이스를 아는 infra가 둘을 잇는다.
 * 트랜잭션 경계(매치 1건 = 트랜잭션 1개)는 core 서비스의 @Transactional이 갖는다.
 */
@Component
class ExpireMatchBridgeAdapter(
	private val expireMatchUseCase: ExpireMatchUseCase,
) : ExpireMatchPort {

	override fun expireSoloMatch(matchId: Long) {
		expireMatchUseCase.expireSoloMatch(matchId)
	}

	override fun expireTeamMatch(teamMatchId: Long) {
		expireMatchUseCase.expireTeamMatch(teamMatchId)
	}
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetExpiredMatchDaoImpl.kt oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/ExpireMatchBridgeAdapter.kt
git commit -m "feat(match): 만료 매칭 조회·core 위임 어댑터 추가"
```

---

### Task 7: api 스케줄러 트리거 + 크론 설정

매시간 배치 잡을 실행한다.

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/scheduler/match/ExpireMatchBatchScheduler.kt`
- Modify: `oneulsogae-api/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `ExpireMatchBatchJob.run()` (Task 5).

- [ ] **Step 1: 스케줄러 작성**

`ExpireMatchBatchScheduler.kt`:

```kotlin
package com.org.oneulsogae.scheduler.match

import com.org.oneulsogae.scheduler.match.command.adapter.ExpireMatchBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료 매칭 정리 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 oneulsogae-scheduler 모듈의 [ExpireMatchBatchJob]을 실행한다. 실행 주기는 oneulsogae.match.expire-batch.cron 프로퍼티로 조정한다.
 * 이 클래스는 api 프로세스에서 "언제 돌릴지"만 책임진다.
 */
@Component
class ExpireMatchBatchScheduler(
	private val expireMatchBatchJob: ExpireMatchBatchJob,
) {

	@Scheduled(cron = "\${oneulsogae.match.expire-batch.cron}", zone = "Asia/Seoul")
	fun runExpireMatch() {
		expireMatchBatchJob.run()
	}
}
```

- [ ] **Step 2: 크론 설정 추가 (운영)**

`application.yml`의 운영 `oneulsogae.match` 블록(`team-match-batch` 다음, 73~75줄 부근)에:

```yaml
    expire-batch:
      # 매시간 정각 (Asia/Seoul). 만료 후 최대 ~1시간 내 정리·환불. ONEULSOGAE_EXPIRE_BATCH_CRON으로 조정
      cron: ${ONEULSOGAE_EXPIRE_BATCH_CRON:0 0 * * * *}
```

- [ ] **Step 3: 크론 설정 추가 (local 프로파일)**

`application.yml`의 `local` 프로파일 `oneulsogae.match` 블록(`team-match-batch` 다음, 112~114줄 부근)에:

```yaml
    expire-batch:
      cron: ${ONEULSOGAE_EXPIRE_BATCH_CRON:0 * * * * *}
```

- [ ] **Step 4: 전체 컨텍스트 기동 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL (전체 빈 와이어링은 Task 8 E2E 기동에서 검증)

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/scheduler/match/ExpireMatchBatchScheduler.kt oneulsogae-api/src/main/resources/application.yml
git commit -m "feat(scheduler): 만료 매칭 정리 배치 매시간 트리거 추가"
```

---

### Task 8: 배치 E2E — 만료 선별·정리·환불·MATCHED 무변경

`RunExpireMatchBatchUseCase`를 끝까지 실행해, 만료 조회가 올바른 행만 잡고 MATCHED는 건드리지 않음을 검증한다.

**Files:**
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunExpireMatchBatchIntegrationTest.kt` (신규)

**Interfaces:**
- Consumes: `RunExpireMatchBatchUseCase.run()` (Task 5), Task 4의 처리 로직, Task 6의 조회/브리지.

- [ ] **Step 1: 실패(미구현 아님 → 동작) 테스트 작성**

`RunExpireMatchBatchIntegrationTest.kt`:

```kotlin
package com.org.oneulsogae.api.scheduler

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.match.command.domain.MatchedTeams
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.SoloMatchEntityFixture
import com.org.oneulsogae.infra.fixture.SoloMatchMemberEntityFixture
import com.org.oneulsogae.infra.match.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QMatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMatchEntity
import com.org.oneulsogae.infra.match.command.entity.SoloMatchEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMatchEntity
import com.org.oneulsogae.infra.popup.command.entity.QPopupEntity
import com.org.oneulsogae.scheduler.match.command.application.port.`in`.RunExpireMatchBatchUseCase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunExpireMatchBatchUseCase](ExpireMatchBatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 만료 조회가 올바른 행만 선별하고, 정리·환불·팝업을 끝까지 수행하며, 성사(MATCHED)는 건드리지 않음을 검증한다.
 */
class RunExpireMatchBatchIntegrationTest(
	private val runExpireMatchBatchUseCase: RunExpireMatchBatchUseCase,
) : AbstractIntegrationSupport({

	describe("run") {
		it("만료된 PARTIALLY_ACCEPTED 솔로·팀은 정리·환불하고, 성사(MATCHED)·미만료는 그대로 둔다") {
			val past: LocalDateTime = LocalDateTime.now().minusHours(1)
			val future: LocalDateTime = LocalDateTime.now().plusHours(1)

			// (1) 만료 솔로 PARTIALLY_ACCEPTED — 정리 + 16 환불 + 소개팅 팝업
			val soloApplicant = 1001L
			val expiredSolo: SoloMatchEntity = IntegrationUtil.persist(
				SoloMatchEntityFixture.create(memberKey = "1001-2001", status = MatchStatus.PARTIALLY_ACCEPTED, expiresAt = past),
			)
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = expiredSolo.id!!, userId = soloApplicant, gender = Gender.MALE, status = MatchMemberStatus.APPLY))
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = expiredSolo.id!!, userId = 2001L, gender = Gender.FEMALE, status = MatchMemberStatus.WAITING))
			IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = soloApplicant, balance = 100))

			// (2) 성사(MATCHED) 솔로 — 무변경 (만료 시각이 과거여도 상태로 제외)
			val matchedSolo: SoloMatchEntity = IntegrationUtil.persist(
				SoloMatchEntityFixture.create(memberKey = "1003-2003", status = MatchStatus.MATCHED, expiresAt = past),
			)
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchedSolo.id!!, userId = 1003L, gender = Gender.MALE, status = MatchMemberStatus.ACTIVE))

			// (3) 미만료 PARTIALLY_ACCEPTED 솔로 — 무변경
			val freshSolo: SoloMatchEntity = IntegrationUtil.persist(
				SoloMatchEntityFixture.create(memberKey = "1004-2004", status = MatchStatus.PARTIALLY_ACCEPTED, expiresAt = future),
			)
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = freshSolo.id!!, userId = 1004L, gender = Gender.MALE, status = MatchMemberStatus.APPLY))

			// (4) 만료 팀 PARTIALLY_ACCEPTED — 정리 + 20 환불 + 미팅 팝업
			val teamApplicant = 3001L
			val expiredTeam: TeamMatchEntity = IntegrationUtil.persist(
				TeamMatchEntity(
					memberKey = MatchedTeams.of(listOf(10L, 20L)).memberKey(),
					introducedDate = LocalDate.now(),
					expiresAt = past,
					status = MatchStatus.PARTIALLY_ACCEPTED,
					matchType = TeamMatchType.DAILY,
					dateInitAmount = 40,
					dateAcceptAmount = 40,
				),
			)
			IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = expiredTeam.id!!, teamId = 10L, status = MatchedTeamStatus.APPLY, applicantUserId = teamApplicant))
			IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = expiredTeam.id!!, teamId = 20L, status = MatchedTeamStatus.WAITING))
			IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = teamApplicant, balance = 100))

			runExpireMatchBatchUseCase.run()

			// 만료 솔로: 정리 + 환불 + 팝업
			soloMatchById(expiredSolo.id!!).shouldBeNull()
			coinBalanceOf(soloApplicant) shouldBe 116
			popupExists(soloApplicant, PopupType.MATCH_FAILED_REFUND) shouldBe true

			// 성사 솔로·미만료 솔로: 그대로
			soloMatchById(matchedSolo.id!!).shouldNotBeNull()
			soloMatchById(freshSolo.id!!).shouldNotBeNull()

			// 만료 팀: 정리 + 환불 + 팝업
			teamMatchById(expiredTeam.id!!).shouldBeNull()
			coinBalanceOf(teamApplicant) shouldBe 120
			popupExists(teamApplicant, PopupType.MEETING_FAILED_REFUND) shouldBe true
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
	}
})

private fun soloMatchById(id: Long): SoloMatchEntity? {
	val q = QSoloMatchEntity.soloMatchEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.id.eq(id)).fetchOne()
}

private fun teamMatchById(id: Long): TeamMatchEntity? {
	val q = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.id.eq(id)).fetchOne()
}

private fun coinBalanceOf(userId: Long): Int {
	val q = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery().select(q.balance).from(q).where(q.userId.eq(userId)).fetchOne()!!
}

private fun popupExists(userId: Long, type: PopupType): Boolean {
	val q = QPopupEntity.popupEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.userId.eq(userId).and(q.popUpType.eq(type))).fetchFirst() != null
}
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.RunExpireMatchBatchIntegrationTest"`
Expected: PASS

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunExpireMatchBatchIntegrationTest.kt
git commit -m "test(scheduler): 만료 매칭 정리 배치 E2E 추가"
```

---

### Task 9: 만료 조회 인덱스

배치 조회 `status IN (...) AND expires_at < now`를 인덱스 seek로 받친다.

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/SoloMatchEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/TeamMatchEntity.kt`
- Create: `docs/migration/match_expires_at_indexes.sql`

- [ ] **Step 1: SoloMatchEntity 인덱스 추가**

`SoloMatchEntity.kt`의 `@Table` `indexes` 배열에 추가(기존 `idx_status` 다음):

```kotlin
		// 만료 정리 배치: status 등치 + expires_at 범위 조회를 받친다.
		Index(name = "idx_status_expires_at", columnList = "status, expires_at"),
```

- [ ] **Step 2: TeamMatchEntity 인덱스 추가**

`TeamMatchEntity.kt`의 `@Table` `indexes` 배열에 같은 인덱스 추가:

```kotlin
		Index(name = "idx_status_expires_at", columnList = "status, expires_at"),
```

- [ ] **Step 3: 마이그레이션 SQL 작성**

`docs/migration/match_expires_at_indexes.sql` 신규:

```sql
-- 만료 정리 배치의 조회(status IN (...) AND expires_at < now)를 인덱스 seek로 받친다.
--   동등 조건(status) → 범위 조건(expires_at) 순서의 복합 인덱스.
ALTER TABLE solo_matches
    ADD INDEX idx_status_expires_at (status, expires_at);

ALTER TABLE team_matches
    ADD INDEX idx_status_expires_at (status, expires_at);
```

- [ ] **Step 4: 컴파일 + 배치 E2E 재확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin && ./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.scheduler.RunExpireMatchBatchIntegrationTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-infra docs/migration/match_expires_at_indexes.sql
git commit -m "perf(match): 만료 정리 배치 조회용 (status, expires_at) 인덱스 추가"
```

---

## 최종 검증

- [ ] 전체 빌드·테스트: `./gradlew build`
- [ ] 신규/변경 테스트 통과: PopupTest, TeamMatchTest, MatchedTeamTest, MatchedTeamsTest, ExpireMatchServiceIntegrationTest, ExpireMatchBatchServiceTest, RunExpireMatchBatchIntegrationTest, (회귀) SendTeamInterestE2ETest.
- [ ] 마이그레이션 SQL 2건이 실DB DDL 반영 대상임을 PR 설명에 명시(`matched_teams_applicant_user_id.sql`, `match_expires_at_indexes.sql`).

## 프론트엔드 영향 안내 (별도, 본 저장소에서 수정하지 않음)

- 팝업 응답 `popupType`에 신규 값 `MEETING_FAILED_REFUND`가 추가된다. 프론트가 popupType별 분기/문구/스타일을 가진다면 새 값 처리가 필요하다.
  대응 위치는 프론트의 팝업 타입 매핑(예: PopupType enum / switch)이며, 솔로 `MATCH_FAILED_REFUND`와 동일하게 환불 안내 팝업으로 노출하면 된다.

## Self-Review 결과

- **Spec 커버리지:** soft-delete(Task 4·6) / 환불(Task 2·4) / 팀 지불자 추적(Task 2·3) / 미팅 팝업(Task 1) / 배치 트리거·주기(Task 5·7) / 인덱스(Task 9) / 매치별 트랜잭션(Task 4) / 테스트(Task 1·2·4·5·8) — 모두 대응 태스크 존재.
- **Placeholder 스캔:** 모든 코드 스텝에 실제 코드 포함, TBD/생략 없음.
- **타입 일관성:** `expireSoloMatch/expireTeamMatch`(Task 4↔5↔6), `findExpiredSoloMatchIds/findExpiredTeamMatchIds`(Task 5↔6), `ExpireMatchBatchResult(soloExpired, teamExpired, soloFailed, teamFailed)`(Task 5 정의↔테스트), `createMeetingFailedRefund`(Task 1↔4), `apply(applicantUserId)`/`respond(teamId, applicantUserId)`/`failureRefunds()`(Task 2 정의↔3·4 사용) 일치 확인.
