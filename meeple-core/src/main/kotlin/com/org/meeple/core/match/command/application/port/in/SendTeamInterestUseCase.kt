package com.org.meeple.core.match.command.application.port.`in`

import com.org.meeple.core.match.command.domain.TeamMatch

/**
 * 팀 매칭 관심 보내기 인포트(유스케이스). 신청과 수락을 하나로 다룬다.
 * 참가 팀의 ACTIVE 구성원이 팀을 대표해 관심을 보낸다. 상대 팀이 아직 신청 안 했으면 신청(→ PARTIALLY_ACCEPTED),
 * 이미 신청했으면 수락이 되어 성사([com.org.meeple.common.match.MatchStatus.MATCHED])된다.
 * 차감 코인(신청/수락 비용)은 팀 매칭 상태로 서버가 산출하며, 행위한 구성원이 부담한다.
 */
interface SendTeamInterestUseCase {

	fun sendInterest(userId: Long, teamMatchId: Long): TeamMatch
}
