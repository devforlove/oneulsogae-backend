package com.org.meeple.core.match.command.application.port.`in`

/**
 * 매칭 참가자 비활성화 인포트(유스케이스).
 * 한 참가자가 채팅방을 나갔지만 방은 닫히지 않을 때(매칭은 유지) 그 참가자의 매칭 참가만 비활성화한다.
 * 다른 도메인(chat)은 이 in-port를 주입해 호출한다.
 */
interface DeactivateMatchMemberUseCase {

	/** [matchId] 매칭에서 [userId] 참가자를 비활성(DEACTIVE)으로 전이한다. 매칭이 이미 없으면 멱등 no-op. */
	fun deactivate(matchId: Long, userId: Long)
}
