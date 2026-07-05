package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.admin.gathering.command.application.port.out.SaveAdminGatheringPort
import com.org.meeple.admin.gathering.command.domain.AdminGathering
import com.org.meeple.infra.gathering.command.mapper.toDomain
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.GatheringJpaRepository
import org.springframework.stereotype.Component

/**
 * [com.org.meeple.infra.gathering.command.entity.GatheringEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 어드민 모임 저장 out-port([SaveAdminGatheringPort])를 구현한다.
 */
@Component
class GatheringAdapter(
	private val gatheringJpaRepository: GatheringJpaRepository,
) : SaveAdminGatheringPort {

	override fun save(gathering: AdminGathering): AdminGathering =
		gatheringJpaRepository.save(gathering.toEntity()).toDomain()
}
