package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.`in`.PurgeWithdrawnUserUseCase
import com.org.oneulsogae.scheduler.user.command.application.port.out.PurgeWithdrawnUserPort
import org.springframework.stereotype.Component

/** scheduler [PurgeWithdrawnUserPort]를 core [PurgeWithdrawnUserUseCase]에 잇는 브리지. (트랜잭션 경계는 core 서비스가 가짐) */
@Component
class PurgeWithdrawnUserBridgeAdapter(
	private val purgeWithdrawnUserUseCase: PurgeWithdrawnUserUseCase,
) : PurgeWithdrawnUserPort {

	override fun purge(userId: Long) {
		purgeWithdrawnUserUseCase.purge(userId)
	}
}
