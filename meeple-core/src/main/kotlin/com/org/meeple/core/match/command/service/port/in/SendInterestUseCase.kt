package com.org.meeple.core.match.command.service.port.`in`

import com.org.meeple.core.match.command.domain.Match

/**
 * 매칭 관심 보내기 인포트(유스케이스). 신청과 수락을 하나로 다룬다.
 * 참가자가 소개받은 매칭에 관심을 보낸다. 상대가 아직 관심을 안 보냈으면 신청(→ PARTIALLY_ACCEPTED),
 * 상대가 이미 보냈으면 수락이 되어 성사([com.org.meeple.common.match.MatchStatus.MATCHED])된다.
 * 차감 코인(신청/수락 비용)은 매칭 상태로 서버가 산출하며, 클라이언트가 금액을 정하지 않는다.
 * 관심 표현 후 갱신된 매칭을 반환한다. (상태 변경 결과만 필요하므로 상대 프로필은 조회하지 않는다)
 */
interface SendInterestUseCase {

	fun sendInterest(userId: Long, matchId: Long): Match
}
