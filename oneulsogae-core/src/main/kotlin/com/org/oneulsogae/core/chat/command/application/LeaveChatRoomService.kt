package com.org.oneulsogae.core.chat.command.application

import com.org.oneulsogae.core.chat.ChatErrorCode
import com.org.oneulsogae.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.oneulsogae.core.chat.command.application.port.`in`.LeaveChatRoomUseCase
import com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomMemberPort
import com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomPort
import com.org.oneulsogae.core.chat.command.domain.ChatRoom
import com.org.oneulsogae.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [LeaveChatRoomUseCase] 구현.
 * 나가려는 사용자의 활성 참가자([com.org.oneulsogae.core.chat.command.domain.ChatRoomMember]) 행을 로드해(없거나 이미 나갔으면 비참가자로 보고 거절) 나가기를 처리한다.
 * 실제 비활성화·마지막 한 명이 나가면 방 종료(소프트 삭제)는 매칭 종료·팀 해체와 동일한 [DeactivateChatRoomMemberUseCase]에 위임해 재사용한다.
 * 단, 남는 상대 나감 안내(SYSTEM 메세지)는 클라이언트가 WebSocket으로 같은 안내를 발행·브로드캐스트하므로 여기선 끈다(notifyRemaining=false) — REST·WebSocket 이중 저장을 막는다.
 * (방 종류[ChatRoom.matchType]·식별자[ChatRoom.matchId]는 로드한 방에서 얻는다. 채팅방 나가기·종료는 연결 매칭을 건드리지 않는다 — 채팅과 매칭 분리)
 * 데드락 방지를 위해 방 행을 비관적 락(FOR UPDATE)으로 먼저 잡아 이 방의 동시 변경(발송·나가기)을 직렬화한 뒤 위임한다(같은 트랜잭션이라 위임도 같은 락 아래에서 처리된다).
 */
@Service
@Transactional
class LeaveChatRoomService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val getChatRoomPort: GetChatRoomPort,
	private val deactivateChatRoomMemberUseCase: DeactivateChatRoomMemberUseCase,
) : LeaveChatRoomUseCase {

	override fun leave(userId: Long, chatRoomId: Long) {
		// 참가자 행은 status 무관하게 로드되므로(프로필 노출 정책), 이미 나간(비활성) 참가자는 여기서 비참가자로 보고 거절한다.
		getChatRoomMemberPort.findByChatRoomIdAndUserId(chatRoomId, userId)
			?.takeIf { it.isActive }
			?: throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)

		// 데드락 방지(게이트): 방 행을 비관적 락(FOR UPDATE)으로 먼저 잡아, 이 방에 대한 동시 변경(발송·나가기)을 직렬화한다.
		val chatRoom: ChatRoom = getChatRoomPort.findByIdForUpdate(chatRoomId)
			?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)

		// 본인 비활성화 + 마지막이면 방 종료까지: 매칭 종료·팀 해체와 동일한 비활성화 로직을 재사용한다. (방 종류/식별자는 방에서 얻는다)
		// 나감 안내(SYSTEM 메세지)는 WebSocket이 발행·브로드캐스트하므로 notifyRemaining=false로 꺼 중복 저장을 막는다.
		deactivateChatRoomMemberUseCase.deactivate(chatRoom.matchType, chatRoom.matchId, listOf(userId), notifyRemaining = false)
	}
}
