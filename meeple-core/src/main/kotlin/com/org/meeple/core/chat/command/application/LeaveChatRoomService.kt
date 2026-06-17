package com.org.meeple.core.chat.command.application

import com.org.meeple.core.chat.ChatErrorCode
import com.org.meeple.core.chat.command.application.port.`in`.LeaveChatRoomUseCase
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomPort
import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.RemoveMatchUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [LeaveChatRoomUseCase] 구현.
 * 나가려는 사용자의 활성 참가자([ChatRoomMember]) 행을 로드해(없거나 이미 나갔으면 비참가자로 보고 거절) 비활성([ChatRoomMember.deactivate])으로 전이한다.
 * 채팅방은 **남은 활성 참가자가 없을 때(이 사용자가 마지막 참가자일 때)만** 닫는다. 이때 방과 참가자 전체를 종료/소프트 삭제하고([ChatRoom.delete], [ChatRoomMembers.delete]) 연결 매칭도 제거한다.
 * (그룹챗에서 한 명이 나가도 다른 참가자가 남아 있으면 방은 ACTIVE를 유지하고 본인 행만 비활성화한다. 1:1도 같은 규칙으로 마지막 한 명이 나갈 때 닫힌다)
 * 전이 전 활성 참가자 수로 판정하므로, 수가 1 이하이면 이 사용자가 마지막이다.
 * 방 닫기·참가자 소프트 삭제·매칭 제거는 모두 **같은 트랜잭션에서 동기로** 처리한다([RemoveMatchUseCase]). 함께 성공하거나 함께 롤백된다. (정합성 보장)
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
	private val removeMatchUseCase: RemoveMatchUseCase,
	private val timeGenerator: TimeGenerator,
) : LeaveChatRoomUseCase {

	override fun leave(userId: Long, chatRoomId: Long) {
		// 참가자 행은 status 무관하게 로드되므로(프로필 노출 정책), 이미 나간(비활성) 참가자는 여기서 비참가자로 보고 거절한다.
		val member: ChatRoomMember = getChatRoomMemberPort.findByChatRoomIdAndUserId(chatRoomId, userId)
			?.takeIf { it.isActive }
			?: throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)

		// 데드락 방지(게이트): 방 행을 비관적 락(FOR UPDATE)으로 먼저 잡아, 이 방에 대한 동시 변경(발송·나가기)을 직렬화한다.
		// 방 락을 가장 먼저 획득하므로, 이후 멤버/방/매칭 쓰기 순서가 어떻든 락 순서가 엇갈려 생기는 데드락이 없다. (matchId도 여기서 얻는다)
		val chatRoom: ChatRoom = getChatRoomPort.findByIdForUpdate(chatRoomId)
			?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)

		// 전이 전 활성 참가자 수. 1 이하이면 이 사용자가 마지막 참가자다. (쓰기 전에 미리 세어 둔다)
		val isLastMember: Boolean = getChatRoomMemberPort.countActiveByChatRoomId(chatRoomId) <= 1L

		if (isLastMember) {
			closeChatRoom(chatRoom)
		} else {
			deactivateMember(member)
		}
	}

	// 마지막 참가자가 나가 방이 닫힌다. 방·참가자 전체·연결 매칭을 모두 종료/소프트 삭제한다. (매칭 제거와 동일한 정리)
	private fun closeChatRoom(chatRoom: ChatRoom) {
		val now: LocalDateTime = timeGenerator.now()
		saveChatRoomPort.save(chatRoom.delete(now))
		saveChatRoomMemberPort.saveAll(getChatRoomMemberPort.findAllByChatRoomId(chatRoom.id).delete(now))
		removeMatchUseCase.remove(chatRoom.matchId)
	}

	// 남은 참가자가 있으면 나가는 사용자의 참가자 행만 비활성화한다. (방·상대는 그대로)
	private fun deactivateMember(member: ChatRoomMember) {
		saveChatRoomMemberPort.save(member.deactivate())
	}
}
