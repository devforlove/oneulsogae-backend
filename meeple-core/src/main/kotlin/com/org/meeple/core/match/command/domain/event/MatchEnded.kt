package com.org.meeple.core.match.command.domain.event

import com.org.meeple.core.match.command.domain.Match

/**
 * 성사된 매칭을 참가자 한쪽이 종료(나감)했을 때 발행되는 도메인 이벤트.
 * 수신측(알람 저장 등)이 후속 처리에 필요한 식별 정보만 담는다.
 * [leftByUserId]는 매칭을 나간 사람, [partnerUserId]는 방에 남아 알람을 받는 상대다.
 * (마지막 참가자가 나가는 경우엔 알릴 상대가 없으므로 발행하지 않는다)
 */
data class MatchEnded(
	val matchId: Long,
	val leftByUserId: Long,
	val partnerUserId: Long,
) {

	companion object {

		/** 매칭과 나간 사람으로부터 이벤트를 만든다. 수신자는 상대 참가자다. */
		fun from(match: Match, leftByUserId: Long): MatchEnded =
			MatchEnded(
				matchId = match.id,
				leftByUserId = leftByUserId,
				partnerUserId = match.partnerOf(leftByUserId),
			)
	}
}
