package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.`in`.ExpireLoungeChatRequestUseCase
import com.org.oneulsogae.scheduler.lounge.command.application.port.out.ExpireLoungeChatRequestPort
import org.springframework.stereotype.Component

/**
 * scheduler [ExpireLoungeChatRequestPort]를 core 만료 유스케이스([ExpireLoungeChatRequestUseCase])에 잇는 브리지 어댑터.
 * scheduler는 core에 의존하지 않으므로(자기 포트만 보유), core의 만료 처리 유스케이스를 아는 infra가 둘을 잇는다.
 * 트랜잭션 경계(신청 1건 = 트랜잭션 1개)는 core 서비스의 @Transactional이 갖는다.
 */
@Component
class ExpireLoungeChatRequestBridgeAdapter(
	private val expireLoungeChatRequestUseCase: ExpireLoungeChatRequestUseCase,
) : ExpireLoungeChatRequestPort {

	override fun expire(requestId: Long) {
		expireLoungeChatRequestUseCase.expire(requestId)
	}
}
