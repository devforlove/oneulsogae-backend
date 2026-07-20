package com.org.oneulsogae.api.chat

import com.org.oneulsogae.chatting.chat.adapter.web.request.ChatReadReportRequest
import com.org.oneulsogae.chatting.chat.adapter.web.response.MessageReadDto
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.chat.command.entity.QChatMessageEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatMessageEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit

/**
 * STOMP `/app/{roomId}/read` 라운드트립 E2E.
 * 한 참가자가 읽음을 보고하면, 같은 방을 구독한 다른 참가자가 읽음 이벤트(MessageReadDto)를 실시간으로 수신하고 DB 포인터도 전진해야 한다.
 */
class MarkMessagesReadStompE2ETest : AbstractIntegrationSupport() {

	companion object {
		private const val SUBSCRIBE_PROPAGATION_DELAY_MS: Long = 500L
	}

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
						Thread.sleep(SUBSCRIBE_PROPAGATION_DELAY_MS)

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
