package com.org.meeple.core.chat.command.application.port.`in`

import com.org.meeple.common.chat.ChatRoomMatchType

/**
 * 매칭의 채팅방에서 특정 사용자들의 참가를 비활성화하는 인포트.
 * 팀 해체 시 나가는 팀원이 (성사된 매칭의) 채팅방에 더는 들어가지 못하도록, 그 팀원들의 참가자 행을 DEACTIVE로 전이한다.
 * 방을 닫지는 않는다. (상대 팀의 참가는 유지)
 */
interface DeactivateChatRoomMemberUseCase {

	/**
	 * [matchType]+[matchId]의 채팅방에서 [userIds] 참가자를 비활성화한다. 채팅방이 없으면 아무것도 하지 않는다.
	 * [notifyRemaining]이 true(기본)면 남는 상대에게 나감 안내 시스템 메세지를 남기고 안 읽음을 갱신한다.
	 * false면 비활성화만 한다. (REST 나가기처럼 안내 메세지를 WebSocket이 따로 발행해 중복을 피해야 하는 경우)
	 */
	fun deactivate(matchType: ChatRoomMatchType, matchId: Long, userIds: List<Long>, notifyRemaining: Boolean = true)
}
