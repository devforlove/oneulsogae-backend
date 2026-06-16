package com.org.meeple.core.match.command.domain.event

import com.org.meeple.core.match.command.domain.Match

/**
 * 매칭에 관심(수락)을 보냈을 때 발행되는 도메인 이벤트.
 * 수신측(알람 저장 등)이 후속 처리에 필요한 식별 정보만 담는다.
 * [recipientUserId]는 관심을 받은 상대(알람 수신자), [senderUserId]는 관심을 보낸 사람이다.
 */
data class InterestSent(
	val matchId: Long,
	val senderUserId: Long,
	val recipientUserId: Long,
) {

	companion object {

		/** 관심을 보낸 매칭과 보낸 사람으로부터 이벤트를 만든다. 수신자는 상대 참가자다. */
		fun from(match: Match, senderUserId: Long): InterestSent =
			InterestSent(
				matchId = match.id,
				senderUserId = senderUserId,
				recipientUserId = match.partnerOf(senderUserId),
			)
	}
}
