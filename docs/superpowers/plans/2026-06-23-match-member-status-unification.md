# MatchMember 상태 모델 통합 (accepted 제거) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `MatchMember.accepted: Boolean?`를 제거하고 참가자 상태를 단일 enum `{WAITING, APPLY, ACTIVE, DEACTIVE}`로 통합한다. 매치 헤더 상태(`MatchStatus`)는 유지한다.

**Architecture:** 멤버 status가 WAITING(소개 직후)→APPLY(관심 신청)→ACTIVE(전원 신청=성사)/DEACTIVE(채팅 나감)를 표현한다. `Match.respond`는 응답자를 APPLY로 바꾸고, 전원 APPLY면 매치를 MATCHED로 만들며 전원을 ACTIVE로 승격한다. 조회·이벤트·서비스의 `MatchStatus` 계약은 보존한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA + QueryDSL / Kotest / Testcontainers(MySQL).

## Global Constraints

- 모든 변수·반환 타입·람다 파라미터 타입 명시. JPQL/QueryDSL 조인은 명시 `join … on`. 시각은 `TimeGenerator.now()`(서비스 코드; 테스트 예외).
- 매치 헤더 상태 `MatchStatus`(PROPOSED/PARTIALLY_ACCEPTED/MATCHED/CLOSED)는 **변경하지 않는다**.
- 멤버 status 매핑: `WAITING`(미신청)·`APPLY`(신청·미성사)·`ACTIVE`(성사·활성)·`DEACTIVE`(채팅 나감). `hasApplied = status ∈ {APPLY, ACTIVE}`.
- 팀(2:2) `TeamMembers`/`Team`는 **건드리지 않는다**(별개 클래스의 `accept`/`allActive`).
- 거절(decline) 상태는 만들지 않는다(WAITING이 무응답 커버).
- `SoloMatchMemberEntity.status`는 `@Enumerated(EnumType.STRING)` varchar — DB enum 변경 불필요.
- DB는 데이터 유실 허용(스키마만 변경). 마이그레이션은 `accepted` 컬럼 DROP만.
- 본 작업은 `main`이 아닌 작업 브랜치에서 수행한다. 시작 전 `git checkout -b refactor/match-member-status`.

---

### Task 1: `MatchMemberStatus` enum 값 추가

참가자 상태에 `WAITING`, `APPLY`를 추가한다. (additive — 단독 컴파일)

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/match/MatchMemberStatus.kt`

**Interfaces:**
- Produces: `MatchMemberStatus.{WAITING, APPLY, ACTIVE, DEACTIVE}`

- [ ] **Step 1: enum 교체**

```kotlin
package com.org.oneulsogae.common.match

/** 매칭(소개) 참가자의 상태. WAITING(대기) → APPLY(신청) → ACTIVE(성사·활성) / DEACTIVE(채팅 나감). */
enum class MatchMemberStatus(val description: String) {

	/** 대기. 소개 직후, 관심 미신청 상태. */
	WAITING("대기"),

	/** 신청. 관심을 신청했으나 매치가 아직 성사되지 않은 상태. */
	APPLY("신청"),

	/** 활성. 전원 신청해 매치가 성사된 활성 상태. */
	ACTIVE("활성"),

	/** 비활성. 채팅방 나가기 등으로 매칭 참가가 비활성화된 상태. */
	DEACTIVE("비활성"),
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-common:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/match/MatchMemberStatus.kt
git commit -m "feat(match): MatchMemberStatus에 WAITING·APPLY 상태 추가"
```

---

### Task 2: core 도메인 (MatchMember / MatchMembers / Match / MatchFixture)

`accepted`를 제거하고 status 기반으로 도메인을 재작성한다. 한 모듈의 원자적 변경(네 파일이 함께 컴파일).

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchMember.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchMembers.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Match.kt`
- Modify: `oneulsogae-core/src/testFixtures/kotlin/com/org/oneulsogae/core/fixture/MatchFixture.kt`

**Interfaces:**
- Consumes: `MatchMemberStatus.{WAITING, APPLY, ACTIVE, DEACTIVE}` (Task 1).
- Produces: `MatchMember.{status, hasApplied, apply(), activate(), deactivate(), delete(now)}`(no `accepted`); `MatchMembers.{allApplied(), anyApplied(), applied(), apply(userId), activateAll(), deactivate(userId), delete(now)}`; `Match.respond(userId)` 동작(전원 APPLY→MATCHED+전원 ACTIVE); `MatchFixture.membersOf(maleUserId, femaleUserId, maleStatus, femaleStatus)`.

- [ ] **Step 1: `MatchMember` 교체** (accepted 제거, apply/activate/hasApplied, 기본 WAITING)

```kotlin
package com.org.oneulsogae.core.match.command.domain

import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.user.Gender
import java.time.LocalDateTime

/**
 * 매칭(소개)에 참가한 사용자 한 명의 참가 정보 도메인 모델.
 * 참가자를 (matchId, userId) 한 쌍의 행으로 정규화해, 1:1뿐 아니라 N:N(2:2·3:3) 미팅으로 확장한다.
 * [status]가 참가자 상태(WAITING→APPLY→ACTIVE/DEACTIVE)를 담는다. 관심 신청 여부는 [hasApplied]로 본다.
 * [deletedAt]이 채워지면 소프트 삭제된(제거된) 참가자다.
 * 영속성은 [com.org.oneulsogae.infra.match.command.entity.SoloMatchMemberEntity]가 담당한다.
 */
data class MatchMember(
	val id: Long = 0,
	val matchId: Long,
	val userId: Long,
	val gender: Gender,
	val status: MatchMemberStatus = MatchMemberStatus.WAITING,
	val deletedAt: LocalDateTime? = null,
) {

	/** 이 참가자가 관심을 신청했는지 여부. (APPLY 또는 ACTIVE) */
	val hasApplied: Boolean
		get() = status == MatchMemberStatus.APPLY || status == MatchMemberStatus.ACTIVE

	/** 이 참가자가 관심을 신청한(APPLY) 새 모델을 반환한다. */
	fun apply(): MatchMember =
		copy(status = MatchMemberStatus.APPLY)

	/** 이 참가자를 활성(ACTIVE)으로 승격한 새 모델을 반환한다. (매치 성사 시) */
	fun activate(): MatchMember =
		copy(status = MatchMemberStatus.ACTIVE)

	/** 이 참가자를 비활성(DEACTIVE)으로 전이한 새 모델을 반환한다. (채팅방 나가기) */
	fun deactivate(): MatchMember =
		copy(status = MatchMemberStatus.DEACTIVE)

	/**
	 * 이 참가자를 [now]에 비활성(DEACTIVE) 전이 + 소프트 삭제(제거)한 새 모델을 반환한다.
	 * 채팅방 나가기로 매칭이 제거될 때 호출한다. 저장하면 status가 DEACTIVE가 되고 deletedAt이 채워져 조회에서 제외된다.
	 */
	fun delete(now: LocalDateTime): MatchMember =
		copy(status = MatchMemberStatus.DEACTIVE, deletedAt = now)
}
```

- [ ] **Step 2: `MatchMembers` 집계 메서드 교체** — 라인 42~64(allAccepted~delete)를 아래로 교체

```kotlin
	/** 모든 참가자가 신청했는지 여부. (참가자가 있고 전원 APPLY/ACTIVE) */
	fun allApplied(): Boolean =
		values.isNotEmpty() && values.all { it.hasApplied }

	/** 한 명이라도 신청했는지 여부. */
	fun anyApplied(): Boolean =
		values.any { it.hasApplied }

	/** 신청(코인 지불)한 참가자들. (환불 대상 산정에 쓴다) */
	fun applied(): List<MatchMember> =
		values.filter { it.hasApplied }

	/** [userId] 참가자를 신청(APPLY) 처리한 새 컬렉션을 반환한다. */
	fun apply(userId: Long): MatchMembers =
		MatchMembers(values.map { if (it.userId == userId) it.apply() else it })

	/** 모든 참가자를 활성(ACTIVE)으로 승격한 새 컬렉션을 반환한다. (매치 성사 시) */
	fun activateAll(): MatchMembers =
		MatchMembers(values.map { it.activate() })

	/** [userId] 참가자만 비활성([MatchMember.deactivate]) 전이한 새 컬렉션을 반환한다. (없으면 그대로) */
	fun deactivate(userId: Long): MatchMembers =
		MatchMembers(values.map { if (it.userId == userId) it.deactivate() else it })

	/** 모든 참가자를 [now]에 소프트 삭제(제거)한 새 컬렉션을 반환한다. */
	fun delete(now: LocalDateTime): MatchMembers =
		MatchMembers(values.map { it.delete(now) })
```

(companion `of`/`memberKeyOf`는 그대로 — `of`는 이제 기본 status WAITING으로 멤버를 만든다.)

- [ ] **Step 3: `Match` 교체** — failureRefunds·hasUserInterest·hasPartnerInterest·respond·withRecomputedStatus를 status 기반으로

`failureRefunds()`(라인 70~73):
```kotlin
	fun failureRefunds(): List<MatchRefund> =
		members.applied()
			.map { member: MatchMember -> MatchRefund(userId = member.userId, amount = datingInitAmount / 2) }
			.filter { refund: MatchRefund -> refund.amount > 0 }
```

`hasUserInterest`/`hasPartnerInterest`(라인 80~85):
```kotlin
	/** 조회 사용자가 이 매칭에 관심(신청)을 보냈는지 여부. (미신청이면 false) */
	fun hasUserInterest(userId: Long): Boolean =
		members.find(userId)?.hasApplied == true

	/** 상대(조회 사용자의 반대편 참가자)가 이 매칭에 관심(신청)을 보냈는지 여부. (미신청이면 false) */
	fun hasPartnerInterest(userId: Long): Boolean =
		members.partnersOf(userId).any { it.hasApplied }
```

`respond`/`withRecomputedStatus`(라인 100~118):
```kotlin
	/**
	 * 참가자의 관심 신청을 반영한 새 상태를 만든다. (참가자/미종료 검증은 호출 측 책임)
	 * 응답자를 APPLY로 바꾸고, 전원 신청이면 매치를 MATCHED로 만들며 전원을 ACTIVE로 승격한다. 일부만 신청이면 PARTIALLY_ACCEPTED, 아무도 미신청이면 PROPOSED.
	 * 성사(MATCHED)되면 만료로 목록에서 사라지지 않게 만료 시각을 100년 뒤로 미룬다.
	 */
	fun respond(userId: Long): Match {
		val applied: Match = copy(members = members.apply(userId))
		val recomputed: Match = applied.withRecomputedStatus()
		return if (recomputed.status == MatchStatus.MATCHED) recomputed.extendExpirationForMatched() else recomputed
	}

	private fun withRecomputedStatus(): Match =
		when {
			members.allApplied() -> copy(status = MatchStatus.MATCHED, members = members.activateAll())
			members.anyApplied() -> copy(status = MatchStatus.PARTIALLY_ACCEPTED)
			else -> copy(status = MatchStatus.PROPOSED)
		}
```

(나머지 — `delete`/`deactivateMember`/`validateRespondable`/`propose` 등 — 변경 없음. `validateRespondable`은 isParticipant·isClosed만 보므로 그대로다.)

- [ ] **Step 4: `MatchFixture.membersOf` 교체** — accepted 파라미터를 status로

`import com.org.oneulsogae.common.match.MatchMemberStatus` 추가, `membersOf`(라인 35~47)를 아래로:
```kotlin
	/** 1:1 참가자(남/녀) 묶음. 각자의 상태를 지정할 수 있다. */
	fun membersOf(
		maleUserId: Long = 1L,
		femaleUserId: Long = 2L,
		maleStatus: MatchMemberStatus = MatchMemberStatus.WAITING,
		femaleStatus: MatchMemberStatus = MatchMemberStatus.WAITING,
	): MatchMembers =
		MatchMembers(
			listOf(
				MatchMember(matchId = 0, userId = maleUserId, gender = Gender.MALE, status = maleStatus),
				MatchMember(matchId = 0, userId = femaleUserId, gender = Gender.FEMALE, status = femaleStatus),
			),
		)
```

- [ ] **Step 5: core 컴파일 확인** (main + testFixtures)

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-core:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL. (oneulsogae-api 테스트는 Task 4까지 깨진 상태 — 여기선 core 컴파일만 본다)

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchMember.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchMembers.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Match.kt \
        oneulsogae-core/src/testFixtures/kotlin/com/org/oneulsogae/core/fixture/MatchFixture.kt
git commit -m "refactor(match): MatchMember를 단일 status 모델로 전환(accepted 제거)"
```

---

### Task 3: infra (엔티티 / 매퍼 / 조회 DAO / 픽스처 / 마이그레이션)

`accepted` 컬럼 제거, 조회 동작 보존, 기본값 WAITING.

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/SoloMatchMemberEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/mapper/MatchMemberMapper.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchWithPartnerDaoImpl.kt`
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/SoloMatchMemberEntityFixture.kt`
- Create: `docs/migration/solo_match_members_drop_accepted.sql`

**Interfaces:**
- Consumes: `MatchMember`(no accepted, status) (Task 2), `MatchMemberStatus.{APPLY, ACTIVE, DEACTIVE, WAITING}` (Task 1).
- Produces: `SoloMatchMemberEntity`(no accepted, status default WAITING); `SoloMatchMemberEntityFixture.create(matchId, userId, gender, status=WAITING)`.

- [ ] **Step 1: `SoloMatchMemberEntity` — accepted 컬럼 제거, status 기본 WAITING**

`accepted` 필드(라인 48~50)를 삭제하고, `status` 기본값을 WAITING으로:
```kotlin
	/** 참가자 상태. WAITING(대기) → APPLY(신청) → ACTIVE(성사·활성) / DEACTIVE(채팅 나감). */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: MatchMemberStatus = MatchMemberStatus.WAITING,
```
클래스 KDoc의 "[accepted]가 참가자별 수락 여부..." 문장을 "참가자 상태([status])는 WAITING→APPLY→ACTIVE/DEACTIVE로 전이하며, 전원 ACTIVE면 매칭이 성사된 것이다."로 갱신.

- [ ] **Step 2: `MatchMemberMapper` — accepted 제거**

`toDomain`/`toEntity`에서 `accepted = accepted,` 줄(라인 14, 29)을 삭제. 결과:
```kotlin
fun SoloMatchMemberEntity.toDomain(): MatchMember =
	MatchMember(
		id = id ?: 0,
		matchId = matchId,
		userId = userId,
		gender = gender,
		status = status,
		deletedAt = deletedAt,
	)

fun MatchMember.toEntity(): SoloMatchMemberEntity =
	SoloMatchMemberEntity(
		matchId = matchId,
		userId = userId,
		gender = gender,
		status = status,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
```

- [ ] **Step 3: `GetMatchWithPartnerDaoImpl` — 필터·프로젝션 status 기반으로**

프로젝션(라인 51~52) `accepted.coalesce(false)` → `status.in(APPLY, ACTIVE)`:
```kotlin
					mySoloMatchMember.status.`in`(MatchMemberStatus.APPLY, MatchMemberStatus.ACTIVE),
					partnerSoloMatchMember.status.`in`(MatchMemberStatus.APPLY, MatchMemberStatus.ACTIVE),
```
where 필터(라인 85) `status.eq(ACTIVE)` → `status.ne(DEACTIVE)`:
```kotlin
				// 내가 나간(DEACTIVE) 매칭만 내 목록에서 제외한다. (대기·신청·활성 매칭은 노출)
				mySoloMatchMember.status.ne(MatchMemberStatus.DEACTIVE),
```
클래스 KDoc의 "관심 여부는 ... accepted ... coalesce(false)..." 주석을 "관심 여부는 참가자 status가 APPLY/ACTIVE인지로 산출한다."로 갱신.

- [ ] **Step 4: `SoloMatchMemberEntityFixture` — accepted 제거, status 기본 WAITING**

```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.match.command.entity.SoloMatchMemberEntity

/**
 * [SoloMatchMemberEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 갓 참가한(대기) 남성 참가자다. (matchId·userId·gender·status를 필요에 맞게 지정)
 */
object SoloMatchMemberEntityFixture {

	fun create(
		matchId: Long = 1L,
		userId: Long = 1L,
		gender: Gender = Gender.MALE,
		status: MatchMemberStatus = MatchMemberStatus.WAITING,
	): SoloMatchMemberEntity =
		SoloMatchMemberEntity(
			matchId = matchId,
			userId = userId,
			gender = gender,
			status = status,
		)
}
```

- [ ] **Step 5: 마이그레이션 SQL 작성**

`docs/migration/solo_match_members_drop_accepted.sql`:
```sql
-- solo_match_members: 참가자 상태를 단일 status enum(WAITING/APPLY/ACTIVE/DEACTIVE)으로 통합하며 accepted 컬럼을 제거한다.
-- (개발 단계로 데이터 보존 환산 없이 컬럼 DROP만. status는 varchar라 새 값 추가에 DDL 불필요)
ALTER TABLE solo_match_members DROP COLUMN accepted;
```

- [ ] **Step 6: infra 컴파일 확인** (main + testFixtures)

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-infra:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/SoloMatchMemberEntity.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/mapper/MatchMemberMapper.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchWithPartnerDaoImpl.kt \
        oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/SoloMatchMemberEntityFixture.kt \
        docs/migration/solo_match_members_drop_accepted.sql
git commit -m "refactor(match): solo_match_member accepted 제거, 조회 status 기반으로 보존"
```

---

### Task 4: 테스트 갱신 + 전체 빌드 검증

도메인 유닛·E2E를 새 status 모델로 갱신하고, 새 동작(전원 신청 시 ACTIVE 승격, 조회 필터 보존)을 단언한다. 이 태스크 종료 시 **전체 빌드 GREEN**.

**Files:**
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchMembersTest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchTest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendInterestE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/GetMatchesE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/chat/LeaveChatRoomE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/scheduler/RunSoloMatchBatchIntegrationTest.kt`

**Interfaces:**
- Consumes: 모든 Task 2·3 산출물.

- [ ] **Step 1: `MatchMembersTest` — apply/신청 집계 + activateAll**

`import com.org.oneulsogae.common.match.MatchMemberStatus` 추가. `describe("accept / 수락 집계")` 블록(라인 31~45)을 아래로 교체:
```kotlin
	describe("apply / 신청 집계") {
		it("한 명만 신청하면 anyApplied=true, allApplied=false") {
			val responded: MatchMembers = members().apply(100L)

			responded.find(100L)!!.hasApplied shouldBe true
			responded.anyApplied() shouldBe true
			responded.allApplied() shouldBe false
		}

		it("전원 신청하면 allApplied=true") {
			val responded: MatchMembers = members().apply(100L).apply(200L)

			responded.allApplied() shouldBe true
		}
	}

	describe("activateAll") {
		it("전원을 ACTIVE로 승격한다") {
			val activated: MatchMembers = members().apply(100L).apply(200L).activateAll()

			activated.values.all { it.status == MatchMemberStatus.ACTIVE } shouldBe true
		}
	}
```

- [ ] **Step 2: `MatchTest` — 성사 시 전원 ACTIVE 단언 추가**

`describe("respond - 성사 시 만료 연장")`의 첫 it("...MATCHED...")에 멤버 status 단언을 추가한다. 해당 it 블록을 아래로 교체(`MatchMemberStatus`는 이미 import됨):
```kotlin
		it("양쪽이 수락해 MATCHED가 되면 만료 시각을 100년 뒤로 미루고 전원 ACTIVE로 승격한다") {
			val proposed: Match = proposedMatch()

			val matched: Match = proposed.respond(maleUserId).respond(femaleUserId)

			matched.status shouldBe MatchStatus.MATCHED
			matched.expiresAt shouldBe proposed.expiresAt.plusYears(Match.MATCHED_EXPIRATION_EXTENSION_YEARS)
			matched.members.values.all { it.status == MatchMemberStatus.ACTIVE } shouldBe true
		}
```
(나머지 it — PARTIALLY 유지 / hasUserInterest / delete→DEACTIVE / 이벤트 — 동작 동일해 그대로 통과한다.)

- [ ] **Step 3: `SendInterestE2ETest` — persistMatch를 status 기반으로**

`import com.org.oneulsogae.common.match.MatchMemberStatus` 추가. `persistMatch` 헬퍼의 파라미터 `maleAccepted: Boolean? = null, femaleAccepted: Boolean? = null`를 `maleStatus: MatchMemberStatus = MatchMemberStatus.WAITING, femaleStatus: MatchMemberStatus = MatchMemberStatus.WAITING`로 바꾸고, 멤버 생성 두 줄(라인 53·56)을:
```kotlin
		IntegrationUtil.persist(
			SoloMatchMemberEntityFixture.create(matchId = matchId, userId = maleUserId, gender = Gender.MALE, status = maleStatus),
		)
		IntegrationUtil.persist(
			SoloMatchMemberEntityFixture.create(matchId = matchId, userId = femaleUserId, gender = Gender.FEMALE, status = femaleStatus),
		)
```
"상대가 이미 관심을 보낸" 컨텍스트의 호출 `persistMatch(maleUserId = ..., femaleUserId = ..., femaleAccepted = true, status = MatchStatus.PARTIALLY_ACCEPTED)`를 `femaleStatus = MatchMemberStatus.APPLY`로 변경:
```kotlin
				val match: SoloMatchEntity = persistMatch(
					maleUserId = maleUserId,
					femaleUserId = femaleUserId,
					femaleStatus = MatchMemberStatus.APPLY,
					status = MatchStatus.PARTIALLY_ACCEPTED,
				)
```
(첫 컨텍스트의 `persistMatch(maleUserId, femaleUserId)`는 기본 WAITING으로 그대로. 기존 단언 — matchStatus·코인·채팅방·알람 — 불변.)

- [ ] **Step 4: `GetMatchesE2ETest` — accepted → status**

`import com.org.oneulsogae.common.match.MatchMemberStatus` 추가. 멤버 생성 두 줄(라인 66·70)을:
```kotlin
				// 나: 미신청(WAITING) → hasUserInterest=false
				IntegrationUtil.persist(
					SoloMatchMemberEntityFixture.create(matchId = matchId, userId = meUserId, gender = Gender.MALE, status = MatchMemberStatus.WAITING),
				)
				// 상대: 신청(APPLY) → hasPartnerInterest=true
				IntegrationUtil.persist(
					SoloMatchMemberEntityFixture.create(matchId = matchId, userId = partnerUserId, gender = Gender.FEMALE, status = MatchMemberStatus.APPLY),
				)
```
(단언 hasUserInterest=false·hasPartnerInterest=true·목록 노출은 불변 — WAITING은 `ne(DEACTIVE)` 필터를 통과한다.)

- [ ] **Step 5: `LeaveChatRoomE2ETest` — 매치 멤버를 ACTIVE로 셋업**

"한 명만 나가면" 컨텍스트(라인 101~102)의 두 멤버 생성에 `status = MatchMemberStatus.ACTIVE`를 추가한다(성사·활성 매치를 전제하므로). `MatchMemberStatus`는 이미 import됨:
```kotlin
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchId, userId = me, gender = Gender.MALE, status = MatchMemberStatus.ACTIVE))
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchId, userId = partner, gender = Gender.FEMALE, status = MatchMemberStatus.ACTIVE))
```
"방 만든 매칭도 제거" 컨텍스트(라인 129~130)의 두 멤버 생성에도 동일하게 `status = MatchMemberStatus.ACTIVE`를 추가한다.
(단언 — me→DEACTIVE / partner→ACTIVE / 매치 제거 — 불변.)

- [ ] **Step 6: `RunSoloMatchBatchIntegrationTest` — persistMatch 멤버 status를 매치 상태에 맞춤**

`import com.org.oneulsogae.common.match.MatchMemberStatus`가 없으면 추가. `persistMatch` 헬퍼에서 멤버 생성 전에 멤버 status를 산정하고 두 멤버에 적용한다:
```kotlin
private fun persistMatch(userIdA: Long, userIdB: Long, status: MatchStatus, introducedDate: LocalDate) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
			status = status,
			introducedDate = introducedDate,
		),
	)
	// 성사(MATCHED) 매치의 멤버는 ACTIVE, 그 외(PROPOSED 등)는 WAITING.
	val memberStatus: MatchMemberStatus = if (status == MatchStatus.MATCHED) MatchMemberStatus.ACTIVE else MatchMemberStatus.WAITING
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdA, gender = Gender.MALE, status = memberStatus))
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdB, gender = Gender.FEMALE, status = memberStatus))
}
```
(`persistMatchedMatchWithLeaver`는 이미 멤버 status를 DEACTIVE/ACTIVE로 명시하므로 변경 없음. "성사 유저 제외" 테스트는 이제 멤버 ACTIVE라 `findMatchedUserIds`에 잡혀 그대로 통과한다.)

- [ ] **Step 7: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 실패 시:
- `accepted`/`isAccepted`/`allAccepted`/`anyAccepted`/`accept(` 잔여 참조가 남았는지 grep(테스트 포함).
- E2E 실패 시: GetMatches(목록 노출=ne(DEACTIVE)), SendInterest(APPLY→MATCHED+ACTIVE), LeaveChat(멤버 ACTIVE 셋업), 배치(MATCHED 멤버 ACTIVE) 셋업을 점검.

- [ ] **Step 8: 커밋**

```bash
git add -A
git commit -m "test(match): MatchMember status 통합에 맞춰 도메인/E2E 테스트 갱신"
```

---

## 완료 기준 (Definition of Done)

- `./gradlew build` 통과.
- `MatchMember.accepted` 및 `isAccepted`/`allAccepted`/`anyAccepted`/`accept()`(MatchMembers/Match 한정) 잔여 참조 0.
- 멤버 status: 생성 WAITING → 관심 신청 APPLY → 전원 신청 시 MATCHED + 전원 ACTIVE → 채팅 나감 DEACTIVE.
- `MatchStatus`·`SendInterestService`·채팅 나가기 서비스 동작 보존, 매칭 목록은 미응답/신청 매치도 노출(`ne(DEACTIVE)`), `hasUserInterest`/`hasPartnerInterest` 계약 보존.
- 팀(`Team`/`TeamMembers`) 무변경.
- E2E/통합(SendInterest·GetMatches·LeaveChat·SoloMatchBatch) GREEN으로 변경 검증.
