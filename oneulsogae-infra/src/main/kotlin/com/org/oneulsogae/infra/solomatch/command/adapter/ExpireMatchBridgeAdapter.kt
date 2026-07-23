package com.org.oneulsogae.infra.solomatch.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.`in`.ExpireLoungeChatRequestUseCase
import com.org.oneulsogae.core.solomatch.command.application.port.`in`.ExpireSoloMatchUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.ExpireTeamMatchUseCase
import com.org.oneulsogae.scheduler.common.command.application.port.out.ExpireMatchPort
import org.springframework.stereotype.Component

/**
 * scheduler [ExpireMatchPort]를 core 만료 유스케이스([ExpireSoloMatchUseCase]·[ExpireTeamMatchUseCase]·[ExpireLoungeChatRequestUseCase])에 잇는 브리지 어댑터.
 * scheduler는 core에 의존하지 않으므로(자기 포트만 보유), core의 만료 처리 유스케이스를 아는 infra가 둘을 잇는다.
 * 솔로·팀·라운지 만료는 각 도메인 유스케이스가 담당하고, 단일 배치 포트([ExpireMatchPort])를 이 어댑터가 분배한다.
 * 트랜잭션 경계(1건 = 트랜잭션 1개)는 core 서비스의 @Transactional이 갖는다.
 */
@Component
class ExpireMatchBridgeAdapter(
	private val expireSoloMatchUseCase: ExpireSoloMatchUseCase,
	private val expireTeamMatchUseCase: ExpireTeamMatchUseCase,
	private val expireLoungeChatRequestUseCase: ExpireLoungeChatRequestUseCase,
) : ExpireMatchPort {

	override fun expireSoloMatch(matchId: Long) {
		expireSoloMatchUseCase.expireSoloMatch(matchId)
	}

	override fun expireTeamMatch(teamMatchId: Long) {
		expireTeamMatchUseCase.expireTeamMatch(teamMatchId)
	}

	override fun expireLoungeChatRequest(requestId: Long) {
		expireLoungeChatRequestUseCase.expire(requestId)
	}
}
