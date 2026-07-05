package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.admin.gathering.command.application.port.out.ActivateAdminGatheringPort
import com.org.meeple.admin.gathering.command.application.port.out.GetAdminGatheringPort
import com.org.meeple.admin.gathering.command.application.port.out.SaveAdminGatheringPort
import com.org.meeple.admin.gathering.command.domain.AdminGathering
import com.org.meeple.admin.gathering.command.domain.AdminGatheringStatus
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.infra.gathering.command.entity.GatheringEntity
import com.org.meeple.infra.gathering.command.mapper.toDomain
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.GatheringJpaRepository
import org.springframework.stereotype.Component

/**
 * [com.org.meeple.infra.gathering.command.entity.GatheringEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 저장([SaveAdminGatheringPort])·상태 로드([GetAdminGatheringPort])·활성화([ActivateAdminGatheringPort]) out-port를 함께 구현한다.
 */
@Component
class GatheringAdapter(
	private val gatheringJpaRepository: GatheringJpaRepository,
) : SaveAdminGatheringPort, GetAdminGatheringPort, ActivateAdminGatheringPort {

	override fun save(gathering: AdminGathering): AdminGathering =
		gatheringJpaRepository.save(gathering.toEntity()).toDomain()

	override fun findById(id: Long): AdminGatheringStatus? =
		gatheringJpaRepository.findById(id)
			.map { entity: GatheringEntity -> AdminGatheringStatus(id = entity.id ?: 0, status = entity.status) }
			.orElse(null)

	// 기존 행을 로드해 status를 모집중(RECRUITING)으로 전이해 저장한다. (다른 필드 보존)
	override fun activate(id: Long) {
		val entity: GatheringEntity = gatheringJpaRepository.findById(id)
			.orElseThrow { IllegalStateException("모임을 찾을 수 없습니다: $id") }
		entity.status = GatheringStatus.RECRUITING
		gatheringJpaRepository.save(entity)
	}
}
