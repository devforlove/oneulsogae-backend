package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.`in`.ExpireMatchUseCase
import com.org.meeple.scheduler.match.command.application.port.out.ExpireMatchPort
import org.springframework.stereotype.Component

/**
 * scheduler [ExpireMatchPort]를 core [ExpireMatchUseCase]에 잇는 브리지 어댑터.
 * scheduler는 core에 의존하지 않으므로(자기 포트만 보유), core의 만료 처리 유스케이스를 아는 infra가 둘을 잇는다.
 * 트랜잭션 경계(매치 1건 = 트랜잭션 1개)는 core 서비스의 @Transactional이 갖는다.
 */
@Component
class ExpireMatchBridgeAdapter(
	private val expireMatchUseCase: ExpireMatchUseCase,
) : ExpireMatchPort {

	override fun expireSoloMatch(matchId: Long) {
		expireMatchUseCase.expireSoloMatch(matchId)
	}

	override fun expireTeamMatch(teamMatchId: Long) {
		expireMatchUseCase.expireTeamMatch(teamMatchId)
	}
}
