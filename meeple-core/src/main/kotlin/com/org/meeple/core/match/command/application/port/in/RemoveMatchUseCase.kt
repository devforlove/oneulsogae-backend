package com.org.meeple.core.match.command.application.port.`in`

/**
 * 매칭 제거 인포트(유스케이스).
 * 채팅방 나가기 등으로 관계가 끝났을 때 그 매칭을 제거한다. 다른 도메인은 이 in-port를 주입해 호출한다.
 */
interface RemoveMatchUseCase {

	/** 매칭을 제거한다. 이미 없으면 멱등 no-op. */
	fun remove(matchId: Long)
}
