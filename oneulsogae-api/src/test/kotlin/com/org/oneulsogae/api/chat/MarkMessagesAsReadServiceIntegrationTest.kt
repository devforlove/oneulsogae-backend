package com.org.oneulsogae.api.chat

import com.org.oneulsogae.chatting.chat.application.MarkMessagesAsReadService
import com.org.oneulsogae.chatting.chat.application.port.`in`.command.MarkMessagesAsReadCommand
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Autowired

/**
 * [MarkMessagesAsReadService] 통합 테스트. (실서버 컨텍스트의 빈을 주입받아 실제 MySQL에 대해 검증)
 * 읽음 포인터 forward-only 전진·뱃지 리셋·역행 무시·비참가자 무변경(no-op)을 확인한다.
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

		fun lastReadAtOf(chatRoomId: Long, userId: Long): LocalDateTime? {
			val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
			return IntegrationUtil.getQuery()
				.select(member.lastReadAt)
				.from(member)
				.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
				.fetchOne()
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
					lastReadAtOf(roomId, me).shouldNotBeNull()
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
				it("활성 참가자 행이 없어 갱신 행 0 → 변화 없이(changed=false) 끝난다") {
					val me = 9121L
					val stranger = 9199L
					val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 93L)).id!!
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))

					val result = sut.markAsRead(MarkMessagesAsReadCommand(chatRoomId = roomId, readerId = stranger, lastReadMessageId = 1L))

					result.changed shouldBe false
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
			IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		}
	}
}
