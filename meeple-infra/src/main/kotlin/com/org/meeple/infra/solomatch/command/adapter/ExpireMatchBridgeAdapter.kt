package com.org.meeple.infra.solomatch.command.adapter

import com.org.meeple.core.solomatch.command.application.port.`in`.ExpireSoloMatchUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.ExpireTeamMatchUseCase
import com.org.meeple.scheduler.match.command.application.port.out.ExpireMatchPort
import org.springframework.stereotype.Component

/**
 * scheduler [ExpireMatchPort]를 core 만료 유스케이스([ExpireSoloMatchUseCase]·[ExpireTeamMatchUseCase])에 잇는 브리지 어댑터.
 * scheduler는 core에 의존하지 않으므로(자기 포트만 보유), core의 만료 처리 유스케이스를 아는 infra가 둘을 잇는다.
 * 솔로·팀 만료는 각 도메인 유스케이스가 담당하고, 단일 배치 포트([ExpireMatchPort])를 이 어댑터가 둘로 분배한다.
 * 트랜잭션 경계(매치 1건 = 트랜잭션 1개)는 core 서비스의 @Transactional이 갖는다.
 */
@Component
class ExpireMatchBridgeAdapter(
	private val expireSoloMatchUseCase: ExpireSoloMatchUseCase,
	private val expireTeamMatchUseCase: ExpireTeamMatchUseCase,
) : ExpireMatchPort {

	override fun expireSoloMatch(matchId: Long) {
		expireSoloMatchUseCase.expireSoloMatch(matchId)
	}

	override fun expireTeamMatch(teamMatchId: Long) {
		expireTeamMatchUseCase.expireTeamMatch(teamMatchId)
	}
}
