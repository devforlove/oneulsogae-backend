package com.org.oneulsogae.api.chat

import com.org.oneulsogae.chatting.chat.application.SendChatMessageService
import com.org.oneulsogae.chatting.chat.application.port.`in`.command.SendChatMessageCommand
import com.org.oneulsogae.common.chat.ChatMessageType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.chat.command.entity.QChatMessageEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
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
