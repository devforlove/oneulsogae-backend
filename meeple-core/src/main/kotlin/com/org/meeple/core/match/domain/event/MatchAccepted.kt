package com.org.meeple.core.match.domain.event

import com.org.meeple.core.match.domain.Match

/**
 * 매칭이 성사(MATCHED)됐을 때 발행되는 도메인 이벤트.
 * 수신측(채팅방 생성·알람 등)이 커밋 이후 후속 처리에 필요한 식별 정보만 담는다.
 * [acceptedByUserId]는 마지막에 수락해 성사를 만든 사용자로, 알람은 그 상대([partnerOfAcceptor])에게 보낸다.
 * (이벤트의 의미·형태는 도메인이 소유하고, 실제 발행은 application 서비스가 한다)
 */
data class MatchAccepted(
	val matchId: Long,
	val maleUserId: Long,
	val femaleUserId: Long,
	val acceptedByUserId: Long,
) {

	/** 수락자의 상대(= 알람 수신자). */
	val partnerOfAcceptor: Long
		get() = if (acceptedByUserId == maleUserId) femaleUserId else maleUserId

	companion object {

		/** 성사된 매칭과 수락자로부터 이벤트를 만든다. (status MATCHED 전제 검증은 호출 측 책임) */
		fun from(match: Match, acceptedByUserId: Long): MatchAccepted =
			MatchAccepted(
				matchId = match.id,
				maleUserId = match.maleUserId,
				femaleUserId = match.femaleUserId,
				acceptedByUserId = acceptedByUserId,
			)
	}
}
