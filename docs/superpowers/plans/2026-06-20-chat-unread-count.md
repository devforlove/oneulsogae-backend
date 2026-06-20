# 채팅 말풍선별 "안 읽은 사람 수" (카톡식) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채팅방 각 메시지(말풍선) 옆에 그 메시지를 아직 읽지 않은 참가자 수를 표시하고, 상대가 읽으면 실시간으로 감소시킨다.

**Architecture:** 멤버별 "읽음 포인터"(`last_read_message_id`)를 진실원천으로 두고, 말풍선별 숫자는 클라이언트가 포인터로 계산한다. 읽음 보고는 STOMP `/app/{roomId}/read`로 받아 포인터를 forward-only로 전진시키고 `/topic/{roomId}`로 읽음 이벤트를 브로드캐스트한다. 히스토리 조회(REST)는 참가자별 포인터/활성여부를 함께 내려준다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4 (Spring Data JPA, STOMP over WebSocket), MySQL, QueryDSL, Kotest + Testcontainers + RestAssured, Spring `WebSocketStompClient`.

## Global Constraints

- **타입 명시**: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다(표현식 본문 함수 포함).
- **모듈 경계**: 실시간 읽음 command는 `meeple-chatting`(core 비의존), 영속성 어댑터는 `meeple-infra`, 조회 read model은 `meeple-core`, HTTP/응답 DTO·E2E는 `meeple-api`.
- **CQRS**: 엔티티 어댑터(command)는 `infra/chat/command/adapter`, 조회 dao는 `infra/chat/query`. 같은 엔티티의 여러 모듈 out-port는 한 어댑터(`ChatRoomMemberAdapter`)에서 함께 구현한다.
- **chatting 발송/읽음 경로는 멤버 도메인을 로드하지 않고 타깃 쿼리(조건부 벌크 UPDATE)로 처리**한다(기존 `updateLastMessageIfActive`·`increaseForOthers` 스타일).
- **forward-only**: 읽음 포인터는 절대 역행하지 않는다(WHERE에 `< :messageId`). 영향 행 0이면 브로드캐스트 생략(멱등).
- **마이그레이션**: 로컬/테스트는 `ddl-auto`(update/create-drop)라 엔티티 컬럼 추가만으로 반영된다. 별도 스크립트 없음. (운영 DB는 수동 DDL 필요 — 본 계획 범위 밖)
- **테스트**: api 경계 → 실서버 E2E(`AbstractIntegrationSupport` + Testcontainers + `IntegrationUtil`/픽스처). 리포지토리 직접 의존 금지(주입 빈 셋업은 `@Autowired` + `init{}` 스타일).

---

## File Structure

**meeple-chatting (실시간 읽음 command):**
- Create `chat/application/port/out/AdvanceReadPointerPort.kt` — 읽음 포인터 전진 out-port
- Create `chat/application/port/in/MarkMessagesAsReadUseCase.kt` — in-port
- Create `chat/application/port/in/command/MarkMessagesAsReadCommand.kt`
- Create `chat/application/port/in/result/MarkMessagesAsReadResult.kt`
- Create `chat/application/MarkMessagesAsReadService.kt` — UseCase 구현
- Create `chat/adapter/web/request/ChatReadReportRequest.kt`
- Create `chat/adapter/web/response/MessageReadDto.kt`
- Modify `chat/adapter/web/ChatMessageController.kt` — `/{roomId}/read` 매핑 + 브로드캐스트
- Modify `chat/application/SendChatMessageService.kt` — 발송 시 발신자 포인터 전진

**meeple-infra (영속성):**
- Modify `chat/command/entity/ChatRoomMemberEntity.kt` — `last_read_message_id` 컬럼
- Modify `chat/command/mapper/ChatRoomMemberMapper.kt` — 필드 매핑
- Modify `chat/command/repository/ChatRoomMemberJpaRepository.kt` — `advanceReadPointer` 쿼리
- Modify `chat/command/adapter/ChatRoomMemberAdapter.kt` — `AdvanceReadPointerPort` 구현
- Modify `chat/query/GetChatParticipantDaoImpl.kt` — 포인터/활성여부 프로젝션

**meeple-core (조회 read model):**
- Modify `chat/command/domain/ChatRoomMember.kt` — `lastReadMessageId` 필드
- Modify `chat/query/dto/ChatParticipant.kt` — `lastReadMessageId`, `active` 필드

**meeple-api (응답/E2E):**
- Modify `chat/response/ChatRoomDetailResponse.kt` — `ChatParticipantResponse`에 필드 추가
- Modify `GetChatRoomDetailE2ETest.kt` — 포인터/활성여부 노출 검증

**meeple-infra testFixtures:**
- Modify `fixture/ChatRoomMemberEntityFixture.kt` — `lastReadMessageId` 파라미터

**meeple-api 테스트:**
- Create `chat/MarkMessagesAsReadServiceIntegrationTest.kt`
- Create `chat/SendChatMessageSenderPointerIntegrationTest.kt`
- Create `chat/StompTestClient.kt` — STOMP 테스트 헬퍼
- Create `chat/MarkMessagesReadStompE2ETest.kt`

---

## Task 1: 읽음 포인터 영속성 + 읽음 처리 유스케이스

새 컬럼 `last_read_message_id`를 추가하고, forward-only 포인터 전진을 수행하는 chatting 유스케이스(`MarkMessagesAsReadService`)와 그 out-port/어댑터/쿼리를 만든다. 서비스 레벨 통합 테스트로 전진·역행무시·뱃지리셋·비참가자 거부를 검증한다.

**Files:**
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/chat/command/entity/ChatRoomMemberEntity.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/chat/command/domain/ChatRoomMember.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/chat/command/mapper/ChatRoomMemberMapper.kt`
- Modify: `meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/ChatRoomMemberEntityFixture.kt`
- Create: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/application/port/out/AdvanceReadPointerPort.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/chat/command/repository/ChatRoomMemberJpaRepository.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/chat/command/adapter/ChatRoomMemberAdapter.kt`
- Create: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/application/port/in/command/MarkMessagesAsReadCommand.kt`
- Create: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/application/port/in/result/MarkMessagesAsReadResult.kt`
- Create: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/application/port/in/MarkMessagesAsReadUseCase.kt`
- Create: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/application/MarkMessagesAsReadService.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/chat/MarkMessagesAsReadServiceIntegrationTest.kt`

**Interfaces:**
- Produces:
  - `AdvanceReadPointerPort.advance(chatRoomId: Long, userId: Long, lastReadMessageId: Long, now: LocalDateTime): Int` — 갱신 행 수 반환
  - `MarkMessagesAsReadCommand(chatRoomId: Long, readerId: Long, lastReadMessageId: Long)`
  - `MarkMessagesAsReadResult(chatRoomId: Long, readerId: Long, lastReadMessageId: Long, changed: Boolean)`
  - `MarkMessagesAsReadUseCase.markAsRead(command: MarkMessagesAsReadCommand): MarkMessagesAsReadResult`
  - `ChatRoomMember.lastReadMessageId: Long?` (core 도메인 필드)
  - `ChatRoomMemberEntity.lastReadMessageId: Long?` (`last_read_message_id` 컬럼)
  - `ChatRoomMemberEntityFixture.create(..., lastReadMessageId: Long? = null)`
- Consumes (기존):
  - `GetChatRoomMemberPort.existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean` (chatting)
  - `TimeGenerator.now(): LocalDateTime` (chatting)

- [ ] **Step 1: 엔티티에 읽음 포인터 컬럼 추가**

`ChatRoomMemberEntity.kt`의 `lastReadAt` 필드(라인 49-51) 바로 다음에 추가:

```kotlin
	/** 이 참가자가 마지막으로 읽은 메세지 id. (한 번도 안 읽었으면 null) 말풍선별 안 읽은 사람 수 계산의 읽음 포인터다. */
	@Column(name = "last_read_message_id")
	var lastReadMessageId: Long? = null,
```

- [ ] **Step 2: core 도메인에 읽음 포인터 필드 추가**

`ChatRoomMember.kt`의 `lastReadAt` 필드(라인 21) 다음에 추가:

```kotlin
	val lastReadMessageId: Long? = null,
```

(데이터 클래스라 기본값 `null`을 줘 `join`/기존 호출부가 깨지지 않는다.)

- [ ] **Step 3: 매퍼에 포인터 매핑 추가**

`ChatRoomMemberMapper.kt`의 `toDomain()`에서 `lastReadAt = lastReadAt,` 다음 줄에 추가:

```kotlin
		lastReadMessageId = lastReadMessageId,
```

같은 파일 `toEntity()`의 엔티티 생성자에 `lastReadAt = lastReadAt,` 다음 줄에 추가:

```kotlin
		lastReadMessageId = lastReadMessageId,
```

- [ ] **Step 4: 픽스처에 포인터 파라미터 추가**

`ChatRoomMemberEntityFixture.kt`의 `create(...)`에 `lastReadAt` 파라미터 다음에 파라미터를 추가하고, 엔티티 생성자에도 전달한다.

파라미터 목록의 `lastReadAt: LocalDateTime? = null,` 다음에:

```kotlin
		lastReadMessageId: Long? = null,
```

엔티티 생성 블록의 `lastReadAt = lastReadAt,` 다음에:

```kotlin
			lastReadMessageId = lastReadMessageId,
```

- [ ] **Step 5: 읽음 포인터 전진 out-port 생성**

`AdvanceReadPointerPort.kt`:

```kotlin
package com.org.meeple.chatting.chat.application.port.out

import java.time.LocalDateTime

/**
 * 읽음 포인터 전진 아웃포트. (chatting 소유, infra 어댑터가 구현)
 * 참가자를 도메인으로 로드하지 않고, 한 참가자의 last_read_message_id를 **forward-only 조건부 UPDATE**로 전진시키며 뱃지(unread_count)를 0으로 되돌린다.
 * 이미 같거나 더 앞선 포인터면 갱신하지 않는다(역행 방지). 갱신된 행 수를 반환한다(0이면 변화 없음 → 브로드캐스트 생략).
 */
interface AdvanceReadPointerPort {

	/** [chatRoomId] 방의 활성 참가자 [userId]의 읽음 포인터를 [lastReadMessageId]로 전진시키고 뱃지를 0으로 리셋한다. (forward-only) 갱신 행 수 반환. */
	fun advance(chatRoomId: Long, userId: Long, lastReadMessageId: Long, now: LocalDateTime): Int
}
```

- [ ] **Step 6: 리포지토리에 forward-only 전진 쿼리 추가**

`ChatRoomMemberJpaRepository.kt`의 `increaseUnreadCountForOthers(...)` 메서드 다음에 추가:

```kotlin
	/**
	 * [chatRoomId] 방의 활성 참가자 [userId]의 읽음 포인터([messageId])를 전진시키고 뱃지를 0으로 리셋한다. 갱신된 행 수를 반환한다.
	 * forward-only: 현재 포인터가 null이거나 [messageId]보다 작을 때만 갱신해 역행을 막는다(순서 뒤바뀐 읽음 프레임 방어).
	 * 벌크 JPQL UPDATE는 @SQLRestriction이 자동 적용되지 않으므로 `deleted_at` 조건을 명시하고, 나간(DEACTIVE) 참가자는 status로 제외한다.
	 */
	@Modifying
	@Query(
		"""
		update ChatRoomMemberEntity m
		set m.lastReadMessageId = :messageId,
		    m.unreadCount = 0,
		    m.lastReadAt = :now
		where m.chatRoomId = :chatRoomId
		  and m.userId = :userId
		  and m.status = :status
		  and m.deletedAt is null
		  and (m.lastReadMessageId is null or m.lastReadMessageId < :messageId)
		""",
	)
	fun advanceReadPointer(
		@Param("chatRoomId") chatRoomId: Long,
		@Param("userId") userId: Long,
		@Param("messageId") messageId: Long,
		@Param("now") now: java.time.LocalDateTime,
		@Param("status") status: ChatRoomMemberStatus,
	): Int
```

- [ ] **Step 7: 어댑터에서 포트 구현**

`ChatRoomMemberAdapter.kt` 상단 import에 추가:

```kotlin
import com.org.meeple.chatting.chat.application.port.out.AdvanceReadPointerPort
import java.time.LocalDateTime
```

클래스 선언의 구현 인터페이스 목록 끝(`IncreaseUnreadCountPort` 뒤)에 `, AdvanceReadPointerPort`를 추가한다:

```kotlin
) : GetChatRoomMemberPort, SaveChatRoomMemberPort, ChattingGetChatRoomMemberPort, IncreaseUnreadCountPort, AdvanceReadPointerPort {
```

클래스 본문 끝, `increaseForOthers(...)` 다음에 구현 메서드 추가:

```kotlin
	// chatting(읽음 경로): 한 참가자의 읽음 포인터를 forward-only로 전진시키고 뱃지를 리셋한다. (갱신 행 수 반환)
	override fun advance(chatRoomId: Long, userId: Long, lastReadMessageId: Long, now: LocalDateTime): Int =
		chatRoomMemberJpaRepository.advanceReadPointer(chatRoomId, userId, lastReadMessageId, now, ChatRoomMemberStatus.ACTIVE)
```

- [ ] **Step 8: command/result/usecase 생성**

`MarkMessagesAsReadCommand.kt`:

```kotlin
package com.org.meeple.chatting.chat.application.port.`in`.command

/**
 * 읽음 보고 커맨드. [readerId]가 [chatRoomId] 방에서 [lastReadMessageId]까지 읽었음을 보고한다.
 * (읽음 포인터는 forward-only로 전진하므로 현재 포인터보다 작은 값은 무시된다)
 */
data class MarkMessagesAsReadCommand(
	val chatRoomId: Long,
	val readerId: Long,
	val lastReadMessageId: Long,
)
```

`MarkMessagesAsReadResult.kt`:

```kotlin
package com.org.meeple.chatting.chat.application.port.`in`.result

/**
 * 읽음 보고 결과(read model). [changed]가 true면 포인터가 실제로 전진한 것이고, false면 변화가 없어(이미 더 앞섬) 브로드캐스트가 불필요하다.
 */
data class MarkMessagesAsReadResult(
	val chatRoomId: Long,
	val readerId: Long,
	val lastReadMessageId: Long,
	val changed: Boolean,
)
```

`MarkMessagesAsReadUseCase.kt`:

```kotlin
package com.org.meeple.chatting.chat.application.port.`in`

import com.org.meeple.chatting.chat.application.port.`in`.command.MarkMessagesAsReadCommand
import com.org.meeple.chatting.chat.application.port.`in`.result.MarkMessagesAsReadResult

/**
 * 읽음 보고 인포트(유스케이스).
 * 보고자가 그 방의 활성 참가자인지 검증한 뒤 읽음 포인터를 forward-only로 전진시키고 뱃지를 리셋한다.
 * 결과의 changed로 실제 전진 여부를 알려, 브로드캐스트(STOMP)는 호출 측(컨트롤러)이 담당한다.
 */
interface MarkMessagesAsReadUseCase {

	fun markAsRead(command: MarkMessagesAsReadCommand): MarkMessagesAsReadResult
}
```

- [ ] **Step 9: 서비스 구현**

`MarkMessagesAsReadService.kt`:

```kotlin
package com.org.meeple.chatting.chat.application

import com.org.meeple.chatting.chat.application.port.`in`.MarkMessagesAsReadUseCase
import com.org.meeple.chatting.chat.application.port.`in`.command.MarkMessagesAsReadCommand
import com.org.meeple.chatting.chat.application.port.`in`.result.MarkMessagesAsReadResult
import com.org.meeple.chatting.chat.application.port.out.AdvanceReadPointerPort
import com.org.meeple.chatting.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.chatting.chat.application.port.out.TimeGenerator
import com.org.meeple.chatting.common.error.ChatException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [MarkMessagesAsReadUseCase] 구현. (chatting 자체 서비스, core 비의존)
 *
 * 발송 경로와 동일하게 참가자를 도메인으로 로드하지 않고 타깃 쿼리로 처리한다.
 * 1) 보고자 참가 검증 — 단건 존재(exists). 비참가자면 위조로 보고 거절.
 * 2) 읽음 포인터 forward-only 전진 + 뱃지 리셋 — 조건부 UPDATE 한 번. 갱신 행 0이면 변화 없음(changed=false).
 *
 * 이 경로는 chat_room_members 단일 행만 UPDATE하고 chat_rooms 락을 잡지 않아, 발송/나가기(방 락 선점)와 락 순서 충돌이 없다.
 */
@Service
@Transactional
class MarkMessagesAsReadService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val advanceReadPointerPort: AdvanceReadPointerPort,
	private val timeGenerator: TimeGenerator,
) : MarkMessagesAsReadUseCase {

	override fun markAsRead(command: MarkMessagesAsReadCommand): MarkMessagesAsReadResult {
		// 1) 보고자 참가 검증 — 활성 참가자만 읽음을 보고할 수 있다.
		if (!getChatRoomMemberPort.existsByChatRoomIdAndUserId(command.chatRoomId, command.readerId)) {
			throw ChatException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}

		// 2) 읽음 포인터 forward-only 전진 + 뱃지 리셋. (갱신 행 0이면 이미 더 앞선 포인터 → 변화 없음)
		val now: LocalDateTime = timeGenerator.now()
		val updated: Int = advanceReadPointerPort.advance(command.chatRoomId, command.readerId, command.lastReadMessageId, now)

		return MarkMessagesAsReadResult(
			chatRoomId = command.chatRoomId,
			readerId = command.readerId,
			lastReadMessageId = command.lastReadMessageId,
			changed = updated > 0,
		)
	}
}
```

- [ ] **Step 10: 실패하는 통합 테스트 작성**

`MarkMessagesAsReadServiceIntegrationTest.kt`:

```kotlin
package com.org.meeple.api.chat

import com.org.meeple.chatting.chat.application.MarkMessagesAsReadService
import com.org.meeple.chatting.chat.application.port.`in`.command.MarkMessagesAsReadCommand
import com.org.meeple.chatting.common.error.ChatException
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

/**
 * [MarkMessagesAsReadService] 통합 테스트. (실서버 컨텍스트의 빈을 주입받아 실제 MySQL에 대해 검증)
 * 읽음 포인터 forward-only 전진·뱃지 리셋·역행 무시·비참가자 거절을 확인한다.
 */
class MarkMessagesAsReadServiceIntegrationTest : AbstractIntegrationSupport() {

	@Autowired
	private lateinit var sut: MarkMessagesAsReadService

	init {
		fun pointerOf(chatRoomId: Long, userId: Long): Long? {
			val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
			return IntegrationUtil.getQuery()
				.select(member.lastReadMessageId)
				.from(member)
				.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
				.fetchOne()
		}

		fun unreadOf(chatRoomId: Long, userId: Long): Int {
			val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
			return IntegrationUtil.getQuery()
				.select(member.unreadCount)
				.from(member)
				.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
				.fetchOne()!!
		}

		describe("MarkMessagesAsReadService.markAsRead") {

			context("활성 참가자가 읽음을 보고하면") {
				it("포인터를 전진시키고 뱃지를 0으로 만든다 (changed=true)") {
					val me = 9101L
					val partner = 9102L
					val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 91L)).id!!
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, unreadCount = 5))
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))

					val result = sut.markAsRead(MarkMessagesAsReadCommand(chatRoomId = roomId, readerId = me, lastReadMessageId = 42L))

					result.changed shouldBe true
					pointerOf(roomId, me) shouldBe 42L
					unreadOf(roomId, me) shouldBe 0
				}
			}

			context("이미 더 앞선 포인터를 가진 참가자가 과거 id로 보고하면") {
				it("역행시키지 않고 changed=false") {
					val me = 9111L
					val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 92L)).id!!
					IntegrationUtil.persist(
						ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, lastReadMessageId = 100L),
					)

					val result = sut.markAsRead(MarkMessagesAsReadCommand(chatRoomId = roomId, readerId = me, lastReadMessageId = 50L))

					result.changed shouldBe false
					pointerOf(roomId, me) shouldBe 100L
				}
			}

			context("참가자가 아닌 사용자가 읽음을 보고하면") {
				it("ChatException(비참가자)을 던진다") {
					val me = 9121L
					val stranger = 9199L
					val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 93L)).id!!
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))

					shouldThrow<ChatException> {
						sut.markAsRead(MarkMessagesAsReadCommand(chatRoomId = roomId, readerId = stranger, lastReadMessageId = 1L))
					}
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
			IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		}
	}
}
```

- [ ] **Step 11: 테스트 실행해 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.MarkMessagesAsReadServiceIntegrationTest"`
Expected: 컴파일 실패 또는 빈 미존재가 아니라, Step 1~9가 이미 적용됐다면 PASS. (만약 Step 9까지 코드를 먼저 다 작성했으면 이 단계는 곧장 PASS가 정상이다. TDD 흐름상 Step 1~9가 구현이고 Step 10이 테스트이므로, 여기서 실패가 보이면 구현 누락을 점검한다.)

- [ ] **Step 12: 테스트 실행해 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.MarkMessagesAsReadServiceIntegrationTest"`
Expected: PASS (3개 컨텍스트 모두 통과)

- [ ] **Step 13: 커밋**

```bash
git add meeple-infra meeple-core meeple-chatting meeple-api/src/test/kotlin/com/org/meeple/api/chat/MarkMessagesAsReadServiceIntegrationTest.kt
git commit -m "$(cat <<'EOF'
feat: 채팅 읽음 포인터(last_read_message_id) 전진 유스케이스 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 발송 시 발신자 읽음 포인터 전진

메시지를 보낸 사람은 그 메시지(및 이전 메시지)를 읽은 것으로 보고, 발송 트랜잭션에서 발신자의 읽음 포인터를 새 메시지 id로 전진시킨다. Task 1의 `AdvanceReadPointerPort`를 재사용한다.

**Files:**
- Modify: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/application/SendChatMessageService.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/chat/SendChatMessageSenderPointerIntegrationTest.kt`

**Interfaces:**
- Consumes: `AdvanceReadPointerPort.advance(chatRoomId, userId, lastReadMessageId, now)` (Task 1), `SendChatMessageUseCase.send(command: SendChatMessageCommand): SentChatMessageResult` (기존)

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`SendChatMessageSenderPointerIntegrationTest.kt`:

```kotlin
package com.org.meeple.api.chat

import com.org.meeple.chatting.chat.application.SendChatMessageService
import com.org.meeple.chatting.chat.application.port.`in`.command.SendChatMessageCommand
import com.org.meeple.common.chat.ChatMessageType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.infra.chat.command.entity.QChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

/**
 * [SendChatMessageService]의 발신자 읽음 포인터 전진 동작 통합 테스트.
 * 발송하면 발신자 포인터는 새 메세지 id로 전진하고, 수신자 포인터는 그대로(null)이며 수신자 뱃지만 +1 되어야 한다.
 */
class SendChatMessageSenderPointerIntegrationTest : AbstractIntegrationSupport() {

	@Autowired
	private lateinit var sut: SendChatMessageService

	init {
		fun pointerOf(chatRoomId: Long, userId: Long): Long? {
			val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
			return IntegrationUtil.getQuery()
				.select(member.lastReadMessageId)
				.from(member)
				.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
				.fetchOne()
		}

		fun unreadOf(chatRoomId: Long, userId: Long): Int {
			val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
			return IntegrationUtil.getQuery()
				.select(member.unreadCount)
				.from(member)
				.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
				.fetchOne()!!
		}

		describe("SendChatMessageService.send - 발신자 읽음 포인터") {

			context("활성 참가자가 메세지를 보내면") {
				it("발신자 포인터를 새 메세지 id로 전진시키고, 수신자 포인터는 그대로 둔다") {
					val me = 9201L
					val partner = 9202L
					val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 94L)).id!!
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))

					val result = sut.send(
						SendChatMessageCommand(chatRoomId = roomId, senderId = me, content = "안녕", type = ChatMessageType.USER),
					)

					pointerOf(roomId, me) shouldBe result.id
					pointerOf(roomId, partner) shouldBe null
					unreadOf(roomId, partner) shouldBe 1
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
			IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
			IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		}
	}
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.SendChatMessageSenderPointerIntegrationTest"`
Expected: FAIL — `pointerOf(roomId, me)`가 `result.id`가 아니라 `null` (아직 발신자 포인터를 전진시키지 않음)

- [ ] **Step 3: 발송 서비스에 발신자 포인터 전진 추가**

`SendChatMessageService.kt` import에 추가:

```kotlin
import com.org.meeple.chatting.chat.application.port.out.AdvanceReadPointerPort
```

생성자에 포트 주입 추가 (`increaseUnreadCountPort` 다음 줄):

```kotlin
	private val advanceReadPointerPort: AdvanceReadPointerPort,
```

`send(...)`에서 `increaseUnreadCountPort.increaseForOthers(...)`(라인 65) 다음에 추가:

```kotlin
		// 5) 발신자는 자기 메세지(및 이전 메세지)를 읽은 것으로 보고 읽음 포인터를 새 메세지로 전진시킨다. (forward-only, 비활성/나간 발신자면 0행)
		advanceReadPointerPort.advance(command.chatRoomId, command.senderId, savedMessage.id, now)
```

또한 클래스 KDoc의 쿼리 수 설명(라인 21 "**4쿼리**")이 5쿼리가 되므로, 주석을 정확히 맞춘다. 라인 21의 문장을 다음으로 교체:

```kotlin
 * 메세지 1건당 DB 접근을 **5쿼리**로 줄였다. (방·참가자를 도메인으로 로드하지 않고 타깃 쿼리로 처리)
```

그리고 4) 항목 설명(라인 25) 다음에 5) 항목 한 줄을 추가:

```kotlin
 * 5) 발신자 읽음 포인터 전진 — **조건부 UPDATE 한 번** (자기 메세지까지 읽음 처리, forward-only)
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.SendChatMessageSenderPointerIntegrationTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add meeple-chatting meeple-api/src/test/kotlin/com/org/meeple/api/chat/SendChatMessageSenderPointerIntegrationTest.kt
git commit -m "$(cat <<'EOF'
feat: 메세지 발송 시 발신자 읽음 포인터 전진

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: STOMP 읽음 보고 엔드포인트 + 실시간 브로드캐스트

`/app/{roomId}/read`로 들어온 읽음 보고를 처리하고, 변화가 있으면 `/topic/{roomId}`로 읽음 이벤트를 브로드캐스트한다. STOMP 라운드트립 E2E로 실시간 전파를 검증한다(테스트용 STOMP 클라이언트 헬퍼 포함).

**Files:**
- Create: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/adapter/web/request/ChatReadReportRequest.kt`
- Create: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/adapter/web/response/MessageReadDto.kt`
- Modify: `meeple-chatting/src/main/kotlin/com/org/meeple/chatting/chat/adapter/web/ChatMessageController.kt`
- Create: `meeple-api/src/test/kotlin/com/org/meeple/api/chat/StompTestClient.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/chat/MarkMessagesReadStompE2ETest.kt`

**Interfaces:**
- Produces:
  - `ChatReadReportRequest(lastReadMessageId: Long)`
  - `MessageReadDto(chatRoomId: Long, readerId: Long, lastReadMessageId: Long)` + `MessageReadDto.from(result: MarkMessagesAsReadResult)`
  - STOMP 목적지 `/app/{roomId}/read` (수신), `/topic/{roomId}`로 `MessageReadDto` 브로드캐스트
- Consumes: `MarkMessagesAsReadUseCase.markAsRead(...)` (Task 1), `principal.userIdOrNull()` (기존 auth)

- [ ] **Step 1: 읽음 보고 요청 DTO 생성**

`ChatReadReportRequest.kt`:

```kotlin
package com.org.meeple.chatting.chat.adapter.web.request

/**
 * 클라이언트가 발행(SEND)하는 읽음 보고 요청. 방을 보고 있는 동안 "여기까지 읽음"을 [lastReadMessageId]로 보고한다.
 * 보고자(readerId)는 인증 Principal에서 서버가 채운다. (클라이언트가 위조할 수 없도록)
 */
data class ChatReadReportRequest(
	val lastReadMessageId: Long,
)
```

- [ ] **Step 2: 읽음 이벤트 브로드캐스트 DTO 생성**

`MessageReadDto.kt`:

```kotlin
package com.org.meeple.chatting.chat.adapter.web.response

import com.org.meeple.chatting.chat.application.port.`in`.result.MarkMessagesAsReadResult

/**
 * 방 구독자에게 브로드캐스트하는 읽음 이벤트(DTO).
 * [readerId]가 [chatRoomId] 방에서 [lastReadMessageId]까지 읽었음을 알린다.
 * 클라이언트는 readerId의 읽음 포인터를 이 값으로 갱신하고, id가 [lastReadMessageId] 이하인 말풍선들의 "안 읽은 사람 수"를 다시 계산한다.
 */
data class MessageReadDto(
	val chatRoomId: Long,
	val readerId: Long,
	val lastReadMessageId: Long,
) {

	companion object {

		/** 읽음 처리 결과([MarkMessagesAsReadResult])를 브로드캐스트 DTO로 변환한다. */
		fun from(result: MarkMessagesAsReadResult): MessageReadDto =
			MessageReadDto(
				chatRoomId = result.chatRoomId,
				readerId = result.readerId,
				lastReadMessageId = result.lastReadMessageId,
			)
	}
}
```

- [ ] **Step 3: 컨트롤러에 읽음 보고 매핑 추가**

`ChatMessageController.kt` import에 추가:

```kotlin
import com.org.meeple.chatting.chat.adapter.web.request.ChatReadReportRequest
import com.org.meeple.chatting.chat.adapter.web.response.MessageReadDto
import com.org.meeple.chatting.chat.application.port.`in`.MarkMessagesAsReadUseCase
import com.org.meeple.chatting.chat.application.port.`in`.command.MarkMessagesAsReadCommand
import com.org.meeple.chatting.chat.application.port.`in`.result.MarkMessagesAsReadResult
```

생성자에 유스케이스 주입 추가 (`sendChatMessageUseCase` 다음 줄):

```kotlin
	private val markMessagesAsReadUseCase: MarkMessagesAsReadUseCase,
```

`sendToRoom(...)` 메서드 다음에 새 매핑 추가:

```kotlin
	/**
	 * `/app/{roomId}/read`로 발행된 읽음 보고를 처리한다.
	 * 보고자(인증 Principal)가 그 방 참가자인지 검증하고 읽음 포인터를 forward-only로 전진시킨다.
	 * 실제로 전진했을 때만(`changed`) 방 구독자(`/topic/{roomId}`)에게 읽음 이벤트를 브로드캐스트한다. (멱등 — 변화 없으면 조용히 끝)
	 */
	@MessageMapping("/{roomId}/read")
	fun markRead(
		@DestinationVariable roomId: Long,
		request: ChatReadReportRequest,
		principal: Principal,
	) {
		val readerId: Long = principal.userIdOrNull() ?: throw ChatException(ChatErrorCode.AUTHENTICATION_REQUIRED)

		val result: MarkMessagesAsReadResult = markMessagesAsReadUseCase.markAsRead(
			MarkMessagesAsReadCommand(chatRoomId = roomId, readerId = readerId, lastReadMessageId = request.lastReadMessageId),
		)

		if (result.changed) {
			messageTemplate.convertAndSend("/topic/$roomId", MessageReadDto.from(result))
		}
	}
```

- [ ] **Step 4: STOMP 테스트 클라이언트 헬퍼 작성**

`StompTestClient.kt`:

```kotlin
package com.org.meeple.api.chat

import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * STOMP-over-SockJS 테스트 클라이언트. (`/ws/chat`이 withSockJS()로 등록돼 있어 SockJsClient로 접속한다)
 * CONNECT 시 Authorization 헤더로 인증하고, SUBSCRIBE 수신을 큐로 모아 테스트에서 await 한다.
 */
class StompTestClient(port: Int, accessToken: String) {

	private val client: WebSocketStompClient = WebSocketStompClient(
		SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient()))),
	).apply {
		messageConverter = MappingJackson2MessageConverter()
	}

	private val session: StompSession

	init {
		val connectHeaders: StompHeaders = StompHeaders().apply {
			add("Authorization", "Bearer $accessToken")
		}
		session = client.connectAsync(
			"http://localhost:$port/ws/chat",
			WebSocketHttpHeaders(),
			connectHeaders,
			object : StompSessionHandlerAdapter() {},
		).get(5, TimeUnit.SECONDS)
	}

	/** [destination]을 구독하고, 수신 페이로드를 [type]으로 역직렬화해 큐에 모은다. */
	fun <T : Any> subscribe(destination: String, type: Class<T>): BlockingQueue<T> {
		val queue: BlockingQueue<T> = LinkedBlockingQueue()
		session.subscribe(
			destination,
			object : StompFrameHandler {
				override fun getPayloadType(headers: StompHeaders): Type = type

				override fun handleFrame(headers: StompHeaders, payload: Any?) {
					if (payload != null) {
						@Suppress("UNCHECKED_CAST")
						queue.add(payload as T)
					}
				}
			},
		)
		return queue
	}

	/** [destination]으로 [payload]를 발행한다. */
	fun send(destination: String, payload: Any) {
		session.send(destination, payload)
	}

	fun disconnect() {
		if (session.isConnected) {
			session.disconnect()
		}
	}
}
```

- [ ] **Step 5: 실패하는 STOMP 라운드트립 E2E 작성**

`MarkMessagesReadStompE2ETest.kt`:

```kotlin
package com.org.meeple.api.chat

import com.org.meeple.chatting.chat.adapter.web.request.ChatReadReportRequest
import com.org.meeple.chatting.chat.adapter.web.response.MessageReadDto
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.infra.chat.command.entity.QChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatMessageEntityFixture
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit

/**
 * STOMP `/app/{roomId}/read` 라운드트립 E2E.
 * 한 참가자가 읽음을 보고하면, 같은 방을 구독한 다른 참가자가 읽음 이벤트(MessageReadDto)를 실시간으로 수신하고 DB 포인터도 전진해야 한다.
 */
class MarkMessagesReadStompE2ETest : AbstractIntegrationSupport() {

	init {
		fun pointerOf(chatRoomId: Long, userId: Long): Long? {
			val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
			return IntegrationUtil.getQuery()
				.select(member.lastReadMessageId)
				.from(member)
				.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
				.fetchOne()
		}

		describe("STOMP /app/{roomId}/read") {

			context("참가자가 읽음을 보고하면") {
				it("방 구독자에게 읽음 이벤트를 브로드캐스트하고 DB 포인터를 전진시킨다") {
					val me = 9301L
					val partner = 9302L
					val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 95L)).id!!
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, unreadCount = 3))
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))
					val lastMessageId: Long = IntegrationUtil.persist(
						ChatMessageEntityFixture.create(chatRoomId = roomId, senderId = partner, content = "안녕"),
					).id!!

					val subscriber = StompTestClient(port, accessTokenFor(partner))
					val reader = StompTestClient(port, accessTokenFor(me))
					try {
						val received = subscriber.subscribe("/topic/$roomId", MessageReadDto::class.java)
						// SUBSCRIBE 프레임이 서버에 등록될 시간을 잠시 준 뒤 발행한다. (SEND가 SUBSCRIBE를 앞질러 이벤트를 놓치지 않도록)
						Thread.sleep(500)

						reader.send("/app/$roomId/read", ChatReadReportRequest(lastReadMessageId = lastMessageId))

						val event: MessageReadDto? = received.poll(5, TimeUnit.SECONDS)
						event.shouldNotBeNull()
						event.chatRoomId shouldBe roomId
						event.readerId shouldBe me
						event.lastReadMessageId shouldBe lastMessageId

						pointerOf(roomId, me) shouldBe lastMessageId
					} finally {
						reader.disconnect()
						subscriber.disconnect()
					}
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
			IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
			IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		}
	}
}
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.MarkMessagesReadStompE2ETest"`
Expected: PASS — 구독자가 5초 내 `MessageReadDto`를 수신하고 DB 포인터가 `lastMessageId`로 전진

(만약 타임아웃되면: ① `/ws/chat` 핸드셰이크 오리진 — 테스트는 동일 호스트라 통과해야 함, ② SUBSCRIBE 대기시간(`Thread.sleep`)을 늘려 재확인, ③ 토큰 활성세션 등록 여부(`accessTokenFor`가 자동 등록)를 확인한다.)

- [ ] **Step 7: 커밋**

```bash
git add meeple-chatting meeple-api/src/test/kotlin/com/org/meeple/api/chat/StompTestClient.kt meeple-api/src/test/kotlin/com/org/meeple/api/chat/MarkMessagesReadStompE2ETest.kt
git commit -m "$(cat <<'EOF'
feat: STOMP 읽음 보고 엔드포인트 + 읽음 이벤트 실시간 브로드캐스트

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 상세 조회에 참가자 읽음 포인터/활성여부 노출

클라이언트가 말풍선별 숫자를 계산할 수 있도록, 방 상세 조회(REST)의 참가자마다 `lastReadMessageId`와 `active`(활성 참가자 여부)를 함께 내려준다.

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/chat/query/dto/ChatParticipant.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/chat/query/GetChatParticipantDaoImpl.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/chat/response/ChatRoomDetailResponse.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/chat/GetChatRoomDetailE2ETest.kt`

**Interfaces:**
- Produces:
  - `ChatParticipant(userId, nickname, profileImageCode, gender, lastReadMessageId: Long?, active: Boolean)`
  - `ChatParticipantResponse(userId, nickname, profileImageCode, gender, lastReadMessageId: Long?, active: Boolean)`
  - 응답 JSON: `data.participants[].lastReadMessageId`, `data.participants[].active`

- [ ] **Step 1: 실패하는 E2E 추가**

`GetChatRoomDetailE2ETest.kt`의 `describe("GET /chat/v1/rooms/{chatRoomId}")` 블록 안, 마지막 `context(...)`들 사이 적당한 위치에 새 context를 추가한다. import에 다음이 없으면 추가: `import org.hamcrest.Matchers.nullValue` (이미 존재), `import com.org.meeple.common.chat.ChatRoomMemberStatus`.

```kotlin
		context("참가자별 읽음 상태를 함께 조회하면") {
			it("각 참가자의 읽음 포인터와 활성여부를 반환한다 (200)") {
				val me = 7501L          // me < left → participants는 userId 오름차순 정렬
				val left = 7502L
				val base: LocalDateTime = LocalDateTime.of(2026, 6, 10, 12, 0)

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = me, nickname = "나"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = left, nickname = "나간사람"))

				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 50L)).id!!
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, lastReadMessageId = 55L),
				)
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(
						chatRoomId = roomId,
						userId = left,
						status = ChatRoomMemberStatus.DEACTIVE,
						lastReadMessageId = 40L,
						exitedAt = base,
					),
				)

				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.participants.size()", 2)
					// 활성 참가자(나): 포인터 55, active true
					body("data.participants[0].userId", me.toInt())
					body("data.participants[0].lastReadMessageId", 55)
					body("data.participants[0].active", true)
					// 나간 참가자: 포인터 40(그대로 노출), active false
					body("data.participants[1].userId", left.toInt())
					body("data.participants[1].lastReadMessageId", 40)
					body("data.participants[1].active", false)
				}
			}
		}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.GetChatRoomDetailE2ETest"`
Expected: FAIL — 응답 JSON에 `lastReadMessageId`/`active` 필드가 없음 (또는 컴파일 실패)

- [ ] **Step 3: read model에 필드 추가**

`ChatParticipant.kt`를 다음으로 교체(필드 2개 추가):

```kotlin
package com.org.meeple.core.chat.query.dto

import com.org.meeple.common.user.Gender

/**
 * 채팅방 참가자 정보(read model).
 * 채팅방 목록/상세 조회에서 참가자의 식별·프로필 표시에 필요한 최소 정보(닉네임·프로필 이미지·성별)를 담는다.
 * 프로필 상세는 다른 도메인(user)이 소유하므로, 서비스/어댑터가 그 도메인의 데이터를 조회해 채운다.
 * 말풍선별 "안 읽은 사람 수" 클라이언트 계산을 위해 읽음 포인터([lastReadMessageId])와 활성여부([active])를 함께 담는다.
 * (나간 참가자는 [active]=false로, 카운트 대상에서 제외하도록 클라이언트가 판단한다)
 */
data class ChatParticipant(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val gender: Gender?,
	val lastReadMessageId: Long?,
	val active: Boolean,
)
```

- [ ] **Step 4: 프로젝션에 포인터/활성여부 추가**

`GetChatParticipantDaoImpl.kt` import에 추가:

```kotlin
import com.org.meeple.common.chat.ChatRoomMemberStatus
```

`Projections.constructor(...)`의 `detail.gender,` 다음에 두 컬럼을 추가:

```kotlin
						member.lastReadMessageId,
						member.status.eq(ChatRoomMemberStatus.ACTIVE),
```

(생성자 인자 순서는 `ChatParticipant`의 프로퍼티 순서와 일치해야 한다: userId, nickname, profileImageCode, gender, lastReadMessageId, active. `member.status.eq(...)`는 `BooleanExpression`이라 `active: Boolean`으로 매핑된다.)

- [ ] **Step 5: 응답 DTO에 필드 추가**

`ChatRoomDetailResponse.kt`의 `ChatParticipantResponse`(라인 38-54)를 다음으로 교체:

```kotlin
/** 채팅방 참여자 정보. [lastReadMessageId]는 그 참가자의 읽음 포인터, [active]는 활성(미퇴장) 참가자 여부다. */
data class ChatParticipantResponse(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	/** 참가자 성별. 아직 설정되지 않았으면 null. */
	val gender: Gender?,
	/** 이 참가자가 마지막으로 읽은 메세지 id. 한 번도 안 읽었으면 null. (말풍선별 안 읽은 사람 수 계산용) */
	val lastReadMessageId: Long?,
	/** 활성(미퇴장) 참가자 여부. false면 나간 참가자라 안 읽은 사람 수 카운트에서 제외한다. */
	val active: Boolean,
) {
	companion object {
		fun of(participant: ChatParticipant): ChatParticipantResponse =
			ChatParticipantResponse(
				userId = participant.userId,
				nickname = participant.nickname,
				profileImageCode = participant.profileImageCode,
				gender = participant.gender,
				lastReadMessageId = participant.lastReadMessageId,
				active = participant.active,
			)
	}
}
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.GetChatRoomDetailE2ETest"`
Expected: PASS — 기존 케이스 + 새 케이스 모두 통과

- [ ] **Step 7: 커밋**

```bash
git add meeple-core meeple-infra meeple-api
git commit -m "$(cat <<'EOF'
feat: 채팅방 상세 조회에 참가자별 읽음 포인터·활성여부 노출

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 전체 회귀 검증

**Files:** (없음 — 검증만)

- [ ] **Step 1: 채팅 관련 전체 테스트 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.chat.*"`
Expected: 신규 4개 + 기존 채팅 E2E 모두 PASS

- [ ] **Step 2: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (전 모듈 컴파일 + 테스트 통과)

- [ ] **Step 3: (선택) PR 생성**

사용자가 요청하면 `feat/chat-unread-count` 브랜치를 푸시하고 PR을 연다.

---

## Self-Review

**1. Spec coverage:**
- 읽음 포인터 컬럼(`last_read_message_id`) → Task 1 Step 1~4 ✓
- forward-only 전진 + 뱃지 리셋 out-port/쿼리/어댑터 → Task 1 Step 5~7 ✓
- chatting 읽음 유스케이스/서비스 → Task 1 Step 8~9 ✓
- 발신자 포인터 전진(발송 시) → Task 2 ✓
- STOMP `/app/{roomId}/read` + `/topic` 브로드캐스트(MessageReadDto) → Task 3 ✓
- 참가자별 `lastReadMessageId`/`active` 노출(REST 상세) → Task 4 ✓
- E2E(STOMP 라운드트립 + REST 노출) + 통합 테스트(전진/역행/비참가자/발신자) → Task 1·2·3·4 테스트 ✓
- 데드락 안전(읽음 경로는 chat_rooms 락 미사용) → `MarkMessagesAsReadService` KDoc에 명시 ✓
- REST `MarkChatRoomAsReadService` 미변경 → 본 계획에서 손대지 않음 ✓
- 범위 밖(누가 읽었는지 목록·다중 인스턴스 전파·클라 렌더링) → 작업 없음(의도적) ✓

**2. Placeholder scan:** "TBD/TODO/적절히 처리" 없음. 모든 코드 스텝에 실제 코드 포함. ✓

**3. Type consistency:**
- `advance(chatRoomId, userId, lastReadMessageId, now): Int` — 포트(Task1 S5)·어댑터(S7)·서비스(S9)·발송(Task2 S3) 동일 ✓
- `advanceReadPointer(chatRoomId, userId, messageId, now, status): Int` — 리포지토리(S6)·어댑터(S7) 동일 ✓
- `MarkMessagesAsReadCommand(chatRoomId, readerId, lastReadMessageId)` — 정의(S8)·서비스(S9)·컨트롤러(Task3 S3)·테스트 동일 ✓
- `MarkMessagesAsReadResult(chatRoomId, readerId, lastReadMessageId, changed)` — 정의(S8)·서비스(S9)·`MessageReadDto.from`(Task3 S2) 동일 ✓
- `MessageReadDto(chatRoomId, readerId, lastReadMessageId)` — 정의(Task3 S2)·E2E 단언(S5) 동일 ✓
- `ChatParticipant(userId, nickname, profileImageCode, gender, lastReadMessageId, active)` — read model(Task4 S3)·프로젝션 순서(S4)·응답 매핑(S5) 동일 ✓
- `ChatRoomMemberEntityFixture.create(..., lastReadMessageId)` — 정의(Task1 S4)·사용(Task1·2·3·4 테스트) 동일 ✓
