# TEAM 채팅방 상대 팀만 participants 노출 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TEAM(2:2) 매칭 채팅방 목록 조회 시 내 팀원을 제외하고 상대 팀 구성원만 participants로 반환한다. (SOLO는 기존대로 나만 제외)

**Architecture:** `chat_room_members`에 `team_id`를 비정규화(생성 시점 스냅샷)한다. 채팅방 생성 시 각 참가자의 team_id를 함께 저장하고(쓰기), 목록 조회는 같은 방의 내 참가자 행을 self-join해 `partner.team_id != me.team_id`로 상대 팀만 남긴다(읽기). SOLO 방은 team_id가 null이라 기존 동작이 그대로 유지된다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4, Spring Data JPA + QueryDSL, Kotest(도메인 유닛), Testcontainers + RestAssured DSL(E2E). 헥사고날 + CQRS.

## Global Constraints

- 응답은 항상 한국어로 한다. `oneulsogae-backend`만 수정한다(프론트엔드 변경 금지 — 응답 DTO 변화 없음).
- 변수·반환 타입·람다 파라미터 타입을 명시한다. `LocalDateTime.now()` 직접 호출 금지(도메인은 `now` 파라미터 주입).
- 스키마 변경은 엔티티 `@Column` + `docs/migration/*.sql` 문서화 + 로컬 `ddl-auto: update` 패턴을 따른다(Flyway 미사용).
- CQRS: command out-port는 `command/adapter`, 조회는 `query`의 `*DaoImpl`. 조회 경로는 부수효과 없음.
- 쿼리는 인덱스 효율 고려: 본 변경의 self-join은 기존 `ux_chat_room_id_user_id`로 seek되며 team_id 신규 인덱스는 불필요.
- 커밋 메시지: `<type>(<domain>): <설명>`. 본 작업 도메인은 `chat`.

---

## File Structure

**쓰기 경로 (Task 1, oneulsogae-core):**
- `oneulsogae-core/.../chat/command/domain/ChatRoomMember.kt` — `teamId` 필드 + `join` 시그니처
- `oneulsogae-core/.../chat/command/application/port/in/command/SaveChatRoomCommand.kt` — `SaveChatRoomParticipant` 추가 + `participants`로 교체
- `oneulsogae-core/.../chat/command/application/SaveChatRoomService.kt` — participants → join(teamId)
- `oneulsogae-core/.../match/command/application/SendInterestService.kt` — SOLO participants(teamId=null)
- `oneulsogae-core/.../match/command/application/SendTeamInterestService.kt` — TEAM participants(teamId=team.id)
- `oneulsogae-api/src/test/.../domain/chat/ChatRoomMemberTest.kt` — join 유닛 테스트 갱신

**읽기/영속 경로 (Task 2, oneulsogae-infra + E2E):**
- `oneulsogae-infra/.../chat/command/entity/ChatRoomMemberEntity.kt` — `team_id` 컬럼
- `oneulsogae-infra/.../chat/command/mapper/ChatRoomMemberMapper.kt` — teamId 왕복
- `oneulsogae-infra/src/testFixtures/.../fixture/ChatRoomMemberEntityFixture.kt` — teamId 파라미터
- `oneulsogae-infra/.../chat/query/GetChatRoomDaoImpl.kt` — self-join 필터
- `docs/migration/chat_room_members_team_id.sql` — ALTER TABLE (신규)
- `oneulsogae-api/src/test/.../api/chat/MyChatRoomsE2ETest.kt` — TEAM 케이스 추가

---

## Task 1: 채팅방 생성 시 참가자 team_id 전달 (쓰기 경로)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/domain/ChatRoomMember.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/application/port/in/command/SaveChatRoomCommand.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/application/SaveChatRoomService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/SendInterestService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/SendTeamInterestService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/chat/ChatRoomMemberTest.kt`

**Interfaces:**
- Produces:
  - `ChatRoomMember.teamId: Long?` (필드, 기본 null)
  - `ChatRoomMember.join(chatRoomId: Long, userId: Long, teamId: Long?, now: LocalDateTime): ChatRoomMember`
  - `data class SaveChatRoomParticipant(val userId: Long, val teamId: Long?)`
  - `SaveChatRoomCommand(matchType, matchId, participants: List<SaveChatRoomParticipant>)`
- Consumes: 없음(이 task가 시작점). `Team.activeMemberIds(): List<Long>`, `Team.id: Long`, `Match.participantUserIds(): List<Long>`는 기존 도메인 제공.

- [ ] **Step 1: 도메인 유닛 테스트를 먼저 수정(실패 유도)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/chat/ChatRoomMemberTest.kt`의 `describe("join")` 블록을 아래로 교체한다. (상단 `val` 영역에 `val teamId: Long = 20L` 추가)

```kotlin
	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val chatRoomId: Long = 10L
	val userId: Long = 1L
	val teamId: Long = 20L

	describe("join") {
		it("TEAM 참가자는 team_id를, 안 읽은 개수 0·미퇴장 상태로 참가시킨다") {
			val member: ChatRoomMember = ChatRoomMember.join(chatRoomId = chatRoomId, userId = userId, teamId = teamId, now = now)

			member.chatRoomId shouldBe chatRoomId
			member.userId shouldBe userId
			member.teamId shouldBe teamId
			member.unreadCount shouldBe 0
			member.lastReadAt shouldBe null
			member.joinedAt shouldBe now
			member.isExited shouldBe false
		}

		it("SOLO 참가자는 team_id가 null이다") {
			val member: ChatRoomMember = ChatRoomMember.join(chatRoomId = chatRoomId, userId = userId, teamId = null, now = now)

			member.teamId shouldBe null
		}
	}
```

- [ ] **Step 2: 테스트 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `join`에 `teamId` 파라미터가 없어 컴파일 에러(`no value passed for parameter` 또는 `too many arguments`).

- [ ] **Step 3: 도메인 모델 수정**

`ChatRoomMember.kt` — `userId` 필드 바로 아래에 `teamId` 필드를 추가한다.

```kotlin
	val id: Long = 0,
	val chatRoomId: Long,
	val userId: Long,
	/** TEAM 매칭 방에서 이 참가자가 속한 팀 id. SOLO 방은 null. 목록 조회에서 내 팀/상대 팀 구분에 쓴다. */
	val teamId: Long? = null,
	val status: ChatRoomMemberStatus = ChatRoomMemberStatus.ACTIVE,
```

같은 파일의 `companion object`의 `join`을 교체한다.

```kotlin
		/** [userId] 사용자를 [chatRoomId] 채팅방에 [now]에 참가시킨 신규 참가자를 생성한다. ([teamId]는 TEAM 방의 소속 팀, SOLO는 null. 안 읽은 개수 0, 미확인 상태) */
		fun join(chatRoomId: Long, userId: Long, teamId: Long?, now: LocalDateTime): ChatRoomMember =
			ChatRoomMember(
				chatRoomId = chatRoomId,
				userId = userId,
				teamId = teamId,
				joinedAt = now,
			)
```

- [ ] **Step 4: 커맨드에 SaveChatRoomParticipant 추가 + participants로 교체**

`SaveChatRoomCommand.kt` 전체를 교체한다.

```kotlin
package com.org.oneulsogae.core.chat.command.application.port.`in`.command

import com.org.oneulsogae.common.chat.ChatRoomMatchType

/**
 * 채팅방 생성 입력. 어느 매칭에서 생성됐는지([matchType]+[matchId])와 참가자 목록([participants])을 받는다.
 * [matchType]은 solo/team 매칭을 구분하는 판별값이다. (match_id가 두 매칭의 id를 함께 가리키므로 타입으로 구분)
 * 참가자는 방이 아니라 참가자(ChatRoomMember) 단위로 보관되므로 목록으로 받는다. (1:1이면 두 명, 그룹챗이면 여러 명)
 * 각 참가자는 TEAM 방에서 소속 팀([SaveChatRoomParticipant.teamId])을 함께 싣는다. (SOLO는 null) 목록 조회의 상대 팀 판별에 쓰인다.
 * 만료 시각/초기 상태는 도메인([com.org.oneulsogae.core.chat.command.domain.ChatRoom.open])이 정하므로 받지 않는다.
 */
data class SaveChatRoomCommand(
	val matchType: ChatRoomMatchType,
	val matchId: Long,
	val participants: List<SaveChatRoomParticipant>,
)

/** 채팅방 참가자 한 명의 생성 입력. [teamId]는 TEAM 방의 소속 팀 id이며 SOLO 방은 null이다. */
data class SaveChatRoomParticipant(
	val userId: Long,
	val teamId: Long?,
)
```

- [ ] **Step 5: SaveChatRoomService가 participants의 teamId를 전달하도록 수정**

`SaveChatRoomService.kt`의 `saveAll(...)` 블록을 교체한다.

```kotlin
		saveChatRoomMemberPort.saveAll(
			ChatRoomMembers(
				command.participants.map { participant: SaveChatRoomParticipant ->
					ChatRoomMember.join(chatRoomId = chatRoom.id, userId = participant.userId, teamId = participant.teamId, now = now)
				},
			),
		)
```

같은 파일 import에 추가한다.

```kotlin
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomParticipant
```

- [ ] **Step 6: SendInterestService(SOLO)가 teamId=null로 participants 구성**

`SendInterestService.kt`의 `completeMatch` 안 `saveChatRoomUseCase.save(...)` 호출을 교체한다.

```kotlin
		saveChatRoomUseCase.save(
			SaveChatRoomCommand(
				matchType = ChatRoomMatchType.SOLO,
				matchId = match.id,
				participants = match.participantUserIds().map { memberId: Long -> SaveChatRoomParticipant(userId = memberId, teamId = null) },
			),
		)
```

import에 추가한다.

```kotlin
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomParticipant
```

- [ ] **Step 7: SendTeamInterestService(TEAM)가 team.id로 participants 구성**

`SendTeamInterestService.kt`의 `completeMatch`를 교체한다.

```kotlin
	/** 성사된 경우: 수락 비용 차감 + 4인 채팅방 생성(동기) + 성사 알림 위임(행위자 제외 양 팀 구성원). */
	private fun completeMatch(userId: Long, teamMatch: TeamMatch, teams: List<Team>): TeamMatch {
		spend(userId, amount = teamMatch.dateAcceptAmount, usageType = CoinUsageType.MEETING_ACCEPT)
		val participants: List<SaveChatRoomParticipant> = teams.flatMap { team: Team ->
			team.activeMemberIds().map { memberId: Long -> SaveChatRoomParticipant(userId = memberId, teamId = team.id) }
		}
		saveChatRoomUseCase.save(
			SaveChatRoomCommand(matchType = ChatRoomMatchType.TEAM, matchId = teamMatch.id, participants = participants),
		)
		domainEventPublisher.publish(
			TeamMatchAccepted(teamMatchId = teamMatch.id, recipientUserIds = participants.map { it.userId }.filter { it != userId }),
		)
		return teamMatch
	}
```

import에 추가한다.

```kotlin
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomParticipant
```

- [ ] **Step 8: 도메인 유닛 테스트 통과 확인 + core 빌드**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-api:test --tests "com.org.oneulsogae.domain.chat.ChatRoomMemberTest"`
Expected: BUILD SUCCESSFUL — join 테스트 2건 통과, oneulsogae-core 컴파일 성공.

- [ ] **Step 9: 커밋**

```bash
git add oneulsogae-core oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/chat/ChatRoomMemberTest.kt
git commit -m "feat(chat): 채팅방 생성 시 참가자 team_id 전달

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: TEAM 채팅방 목록에서 상대 팀만 participants로 노출 (영속 + 읽기 경로)

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/chat/command/entity/ChatRoomMemberEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/chat/command/mapper/ChatRoomMemberMapper.kt`
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/ChatRoomMemberEntityFixture.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/chat/query/GetChatRoomDaoImpl.kt`
- Create: `docs/migration/chat_room_members_team_id.sql`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/chat/MyChatRoomsE2ETest.kt`

**Interfaces:**
- Consumes (Task 1): `ChatRoomMember.teamId: Long?`
- Produces:
  - `ChatRoomMemberEntity.teamId: Long?` (컬럼 `team_id`) → QueryDSL `QChatRoomMemberEntity.teamId` 생성
  - `ChatRoomMemberEntityFixture.create(..., teamId: Long? = null)`
  - 동작: `GetChatRoomDao.findActiveByUserId(userId)`가 TEAM 방에서 상대 팀만 반환

- [ ] **Step 1: 엔티티에 team_id 컬럼 추가**

`ChatRoomMemberEntity.kt`의 `userId` 프로퍼티 바로 아래에 추가한다.

```kotlin
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** TEAM 매칭 방에서 이 참가자가 속한 팀 id. SOLO 방은 null. 같은 방 내 상대 팀 판별(내 행과 비교)에 쓴다. */
	@Column(name = "team_id")
	val teamId: Long? = null,
```

- [ ] **Step 2: 매퍼에 teamId 왕복 추가**

`ChatRoomMemberMapper.kt`의 `toDomain()`에 `teamId` 매핑을 추가한다(`userId` 아래).

```kotlin
fun ChatRoomMemberEntity.toDomain(): ChatRoomMember =
	ChatRoomMember(
		id = id ?: 0,
		chatRoomId = chatRoomId,
		userId = userId,
		teamId = teamId,
		status = status,
		unreadCount = unreadCount,
		lastReadAt = lastReadAt,
		lastReadMessageId = lastReadMessageId,
		joinedAt = joinedAt,
		exitedAt = exitedAt,
		deletedAt = deletedAt,
	)
```

같은 파일 `toEntity()`에도 추가한다(`userId` 아래).

```kotlin
fun ChatRoomMember.toEntity(): ChatRoomMemberEntity =
	ChatRoomMemberEntity(
		chatRoomId = chatRoomId,
		userId = userId,
		teamId = teamId,
		status = status,
		unreadCount = unreadCount,
		lastReadAt = lastReadAt,
		lastReadMessageId = lastReadMessageId,
		joinedAt = joinedAt,
		exitedAt = exitedAt,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
```

- [ ] **Step 3: 픽스처에 teamId 파라미터 추가**

`ChatRoomMemberEntityFixture.kt`의 `create`에 `teamId` 파라미터를 추가한다(`userId` 아래).

```kotlin
	fun create(
		chatRoomId: Long = 1L,
		userId: Long = 1L,
		teamId: Long? = null,
		status: ChatRoomMemberStatus = ChatRoomMemberStatus.ACTIVE,
		unreadCount: Int = 0,
		lastReadAt: LocalDateTime? = null,
		lastReadMessageId: Long? = null,
		joinedAt: LocalDateTime = LocalDateTime.now(),
		exitedAt: LocalDateTime? = null,
	): ChatRoomMemberEntity =
		ChatRoomMemberEntity(
			chatRoomId = chatRoomId,
			userId = userId,
			teamId = teamId,
			status = status,
			unreadCount = unreadCount,
			lastReadAt = lastReadAt,
			lastReadMessageId = lastReadMessageId,
			joinedAt = joinedAt,
			exitedAt = exitedAt,
		)
```

- [ ] **Step 4: 마이그레이션 SQL 작성**

Create `docs/migration/chat_room_members_team_id.sql`:

```sql
-- chat_room_members: TEAM 채팅방에서 내 팀/상대 팀 구분을 위해 생성 시점의 team_id를 스냅샷으로 보관한다.
-- SOLO 방은 NULL. 목록 조회는 같은 방의 내 참가자 행과 team_id를 비교해(self-join, ux_chat_room_id_user_id로 seek) 상대 팀만 노출한다.
-- team_id는 seek 키가 아니라 비교 컬럼이라 별도 인덱스를 두지 않는다. (운영에 기존 TEAM 채팅방이 없어 백필 불필요)
ALTER TABLE chat_room_members
    ADD COLUMN team_id BIGINT NULL;
```

- [ ] **Step 5: 영속 계층 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-infra:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL — 엔티티/매퍼/픽스처 컴파일 성공(`QChatRoomMemberEntity.teamId` 생성).

- [ ] **Step 6: 실패하는 E2E 테스트 작성**

`MyChatRoomsE2ETest.kt`에 다음을 추가한다.

(a) import 추가:

```kotlin
import com.org.oneulsogae.common.chat.ChatRoomMatchType
```

(b) 기존 `openRoomWithMembers` 헬퍼 바로 아래에 TEAM 방 헬퍼를 추가한다.

```kotlin
	// TEAM(2:2) 방과 참가자 행들을 함께 저장한다. members: (userId, unreadCount, teamId) 목록.
	fun openTeamRoomWithMembers(
		matchId: Long,
		members: List<Triple<Long, Int, Long?>>,
	): Long {
		val room: ChatRoomEntity = IntegrationUtil.persist(
			ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = matchId),
		)
		val roomId: Long = room.id!!
		members.forEach { (memberUserId: Long, unreadCount: Int, teamId: Long?) ->
			IntegrationUtil.persist(
				ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = memberUserId, unreadCount = unreadCount, teamId = teamId),
			)
		}
		return roomId
	}
```

(c) `describe("GET /chat/v1/rooms")` 안 마지막 context 뒤에 새 context를 추가한다.

```kotlin
			context("TEAM(2:2) 매칭 채팅방을 조회하면") {
				it("내 팀원을 제외하고 상대 팀 구성원만 participants로 내려준다") {
					val userId = 7201L
					val teammate = 7202L
					val opponentA = 7203L
					val opponentB = 7204L
					val myTeamId = 100L
					val oppTeamId = 200L

					// 팀원도 프로필을 둬서, 팀원이 '프로필 없음'이 아니라 팀 필터로 빠지는 것을 검증한다
					IntegrationUtil.persist(UserDetailEntityFixture.create(userId = teammate, nickname = "내팀원", gender = Gender.MALE))
					IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentA, nickname = "상대A", gender = Gender.FEMALE))
					IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentB, nickname = "상대B", gender = Gender.FEMALE))

					openTeamRoomWithMembers(
						matchId = 50L,
						members = listOf(
							Triple(userId, 3, myTeamId),
							Triple(teammate, 0, myTeamId),
							Triple(opponentA, 0, oppTeamId),
							Triple(opponentB, 0, oppTeamId),
						),
					)

					get("/chat/v1/rooms") {
						bearer(accessTokenFor(userId))
					} expect {
						status(200)
						body("success", true)
						body("data.size()", 1)
						// 상대 팀 2명만 (내 팀원 제외)
						body("data[0].participants.size()", 2)
						body("data[0].participants.userId", hasItems(opponentA.toInt(), opponentB.toInt()))
						body("data[0].unreadCount", 3) // 본인 관점
					}
				}
			}
```

- [ ] **Step 7: E2E 실패 확인(버그 재현)**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.chat.MyChatRoomsE2ETest"`
Expected: FAIL — `data[0].participants.size()`가 2가 아니라 **3**(내 팀원 `teammate`까지 포함). 조회가 아직 팀 필터를 하지 않기 때문.

- [ ] **Step 8: 조회에 self-join 팀 필터 추가**

`GetChatRoomDaoImpl.kt`의 `findPartnerParticipants`를 교체한다. (내 행을 self-join해 `partner.team_id != me.team_id`로 상대 팀만 남긴다. SOLO는 `me.team_id is null`이라 전원 유지)

```kotlin
	private fun findPartnerParticipants(roomIds: List<Long>, userId: Long): Map<Long, List<ChatParticipant>> {
		val partner: QChatRoomMemberEntity = QChatRoomMemberEntity("partner")
		val me: QChatRoomMemberEntity = QChatRoomMemberEntity("me")
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.from(partner)
			.join(me).on(me.chatRoomId.eq(partner.chatRoomId).and(me.userId.eq(userId)))
			.join(userDetail).on(userDetail.userId.eq(partner.userId))
			.where(
				partner.chatRoomId.`in`(roomIds),
				partner.userId.ne(userId),
				me.teamId.isNull.or(partner.teamId.ne(me.teamId)),
			)
			.orderBy(partner.chatRoomId.asc(), partner.userId.asc())
			.transform(
				GroupBy.groupBy(partner.chatRoomId).`as`(
					GroupBy.list(
						Projections.constructor(
							ChatParticipant::class.java,
							partner.userId,
							userDetail.nickname,
							userDetail.profileImageCode,
							userDetail.gender,
							partner.lastReadMessageId,
							partner.status.eq(ChatRoomMemberStatus.ACTIVE),
						),
					),
				),
			)
	}
```

참고: `me`는 `(chat_room_id, user_id)` 유니크 행이고 `roomIds` 자체가 내 ACTIVE 행에서 나왔으므로 항상 매칭된다. self-join은 `ux_chat_room_id_user_id`로 seek된다. 메서드 상단 KDoc은 그대로 두되, 필요 시 "상대 팀만(내 행 team_id와 다른 팀) 남긴다"는 한 줄을 보강한다.

- [ ] **Step 9: E2E 전체 통과 확인(TEAM 신규 + SOLO 회귀)**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.chat.MyChatRoomsE2ETest"`
Expected: PASS — 신규 TEAM 케이스(상대 2명만) 통과 + 기존 SOLO/그룹 케이스 전부 통과(team_id null이라 나만 제외 동작 유지).

- [ ] **Step 10: 커밋**

```bash
git add oneulsogae-infra docs/migration/chat_room_members_team_id.sql oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/chat/MyChatRoomsE2ETest.kt
git commit -m "feat(chat): TEAM 채팅방 목록에서 상대 팀만 participants로 노출

chat_room_members.team_id 비정규화 + 내 행 self-join으로 상대 팀만 필터.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 검증 (전체)

- [ ] **최종: 전체 빌드/테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — 전 모듈 컴파일 + 유닛/E2E 테스트 통과.

## 운영 반영 메모

- 실DB에는 `docs/migration/chat_room_members_team_id.sql`의 `ALTER TABLE`을 수동 실행한다(로컬은 `ddl-auto: update`가 자동 반영).
- 기존 TEAM 채팅방이 없어 백필은 없다. 이후 생성되는 TEAM 방부터 team_id가 채워진다.

## Self-Review 결과

- **스펙 커버리지:** 스키마(Task2-S1,4) · 도메인(Task1-S3) · 커맨드 구조체(Task1-S4) · 호출부 2곳(Task1-S6,7) · 매퍼(Task2-S2) · 조회 self-join(Task2-S8) · 도메인 유닛(Task1-S1) · E2E(Task2-S6) · 백필 없음(메모) 모두 매핑됨.
- **플레이스홀더:** 없음(모든 코드 블록 실제 코드).
- **타입 일관성:** `join(chatRoomId, userId, teamId, now)` / `SaveChatRoomParticipant(userId, teamId)` / `ChatRoomMemberEntity.teamId` / `QChatRoomMemberEntity("partner"|"me").teamId` 명칭이 Task 전반에서 일치.
