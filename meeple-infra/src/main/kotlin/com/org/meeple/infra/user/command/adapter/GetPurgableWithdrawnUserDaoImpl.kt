package com.org.meeple.infra.user.command.adapter

import com.org.meeple.infra.user.command.repository.UserJpaRepository
import com.org.meeple.scheduler.user.command.application.port.out.GetPurgableWithdrawnUserPort
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** [GetPurgableWithdrawnUserPort] 구현. 소프트삭제 행을 다루므로 네이티브 조회를 사용한다. */
@Component
class GetPurgableWithdrawnUserDaoImpl(
	private val userJpaRepository: UserJpaRepository,
) : GetPurgableWithdrawnUserPort {

	override fun findUserIdsWithdrawnBefore(cutoff: LocalDateTime): List<Long> =
		userJpaRepository.findPurgableUserIds(cutoff)
}
