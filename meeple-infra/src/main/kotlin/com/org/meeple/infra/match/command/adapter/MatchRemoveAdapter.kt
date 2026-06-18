package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.`in`.RemoveMatchUseCase
import com.org.meeple.scheduler.match.command.application.port.out.RemoveMatchPort
import org.springframework.stereotype.Component

/**
 * scheduler의 [RemoveMatchPort]를 core의 [RemoveMatchUseCase]에 잇는 브리지 어댑터.
 * 매칭 제거 로직(소프트 삭제 + 코인 환불 + 환불 팝업)은 core가 갖고 있고, scheduler는 core에 의존하지 않으므로
 * 둘을 모두 아는 infra가 scheduler 아웃포트를 core 인포트 호출로 위임한다. (영속성이 아니라 모듈 간 위임이라 별도 어댑터로 둔다)
 */
@Component
class MatchRemoveAdapter(
	private val removeMatchUseCase: RemoveMatchUseCase,
) : RemoveMatchPort {

	override fun remove(matchId: Long) {
		removeMatchUseCase.remove(matchId)
	}
}
