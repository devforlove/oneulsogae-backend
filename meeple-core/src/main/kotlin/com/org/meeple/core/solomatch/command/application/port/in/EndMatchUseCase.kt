package com.org.meeple.core.solomatch.command.application.port.`in`

/**
 * 성사된 1:1 매칭을 종료하는 인포트.
 * 참가자 한쪽이 매칭을 끝내면, 매칭을 종료(CLOSED)·소프트 삭제하고 연결된 채팅방에서 본인을 내보낸 뒤 상대에게 나감을 알린다.
 */
interface EndMatchUseCase {

	/** [userId]가 [matchId] 매칭을 종료한다. (참가자·성사 여부 검증 후 매칭 제거 + 채팅 참가 비활성화 + 상대 안내) */
	fun endMatch(userId: Long, matchId: Long)
}
