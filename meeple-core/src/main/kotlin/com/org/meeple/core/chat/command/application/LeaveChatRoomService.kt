package com.org.meeple.core.chat.command.application

import com.org.meeple.core.chat.ChatErrorCode
import com.org.meeple.core.chat.command.application.port.`in`.LeaveChatRoomUseCase
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomPort
import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [LeaveChatRoomUseCase] 구현.
 * 나가려는 사용자의 활성 참가자([ChatRoomMember]) 행을 로드해(없거나 이미 나갔으면 비참가자로 보고 거절) 비활성([ChatRoomMember.deactivate])으로 전이한다.
 * 채팅방은 **남은 활성 참가자가 없을 때(이 사용자가 마지막 참가자일 때)만** 종료([ChatRoom.close])한다.
 * (그룹챗에서 한 명이 나가도 다른 참가자가 남아 있으면 방은 ACTIVE를 유지하고, 1:1도 같은 규칙으로 마지막 한 명이 나갈 때 닫힌다)
 * 전이 전 활성 참가자 수로 판정하므로, 수가 1 이하이면 이 사용자가 마지막이다.
 * 비활성(DEACTIVE) 참가자는 활성 참가자 조회(접근 검증·종료 판정·방 목록)에서 status 기준으로 제외된다. (프로필 노출은 유지)
 * "OO님이 나갔어요" 안내 메세지는 클라이언트가 나가기 직전 SYSTEM 타입 메세지로 발행한다. (서버는 발행 경로에서 저장·브로드캐스트)
 */
@Service
@Transactional
class LeaveChatRoomService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
	private val getChatRoomPort: GetChatRoomPort,
	private val saveChatRoomPort: SaveChatRoomPort,
) : LeaveChatRoomUseCase {

	override fun leave(userId: Long, chatRoomId: Long) {
		// 참가자 행은 status 무관하게 로드되므로(프로필 노출 정책), 이미 나간(비활성) 참가자는 여기서 비참가자로 보고 거절한다.
		val member: ChatRoomMember = getChatRoomMemberPort.findByChatRoomIdAndUserId(chatRoomId, userId)
			?.takeIf { it.isActive }
			?: throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)

		// 전이 전 활성 참가자 수. 1 이하이면 이 사용자가 마지막 참가자다. (비활성 전이 후에는 0명)
		val isLastMember: Boolean = getChatRoomMemberPort.countActiveByChatRoomId(chatRoomId) <= 1L

		saveChatRoomMemberPort.save(member.deactivate())

		if (isLastMember) {
			val chatRoom: ChatRoom = getChatRoomPort.findById(chatRoomId)
				?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
			saveChatRoomPort.save(chatRoom.close())
		}
	}
}
