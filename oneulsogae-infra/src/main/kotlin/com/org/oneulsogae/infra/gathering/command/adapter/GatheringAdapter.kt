package com.org.oneulsogae.infra.gathering.command.adapter

import com.org.oneulsogae.admin.gathering.command.application.port.out.ChangeAdminGatheringStatusPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.GetAdminGatheringPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadAdminGatheringPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.SaveAdminGatheringPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.UpdateAdminGatheringPort
import com.org.oneulsogae.admin.gathering.command.domain.AdminGathering
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringStatus
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity
import com.org.oneulsogae.infra.gathering.command.mapper.toDomain
import com.org.oneulsogae.infra.gathering.command.mapper.toEntity
import com.org.oneulsogae.infra.gathering.command.repository.GatheringJpaRepository
import org.springframework.stereotype.Component

/**
 * [com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 저장([SaveAdminGatheringPort])·상태 로드([GetAdminGatheringPort])·상태 전이([ChangeAdminGatheringStatusPort])·
 * 전체 로드([LoadAdminGatheringPort])·전체 수정([UpdateAdminGatheringPort]) out-port를 함께 구현한다.
 */
@Component
class GatheringAdapter(
	private val gatheringJpaRepository: GatheringJpaRepository,
) : SaveAdminGatheringPort,
	GetAdminGatheringPort,
	ChangeAdminGatheringStatusPort,
	LoadAdminGatheringPort,
	UpdateAdminGatheringPort {

	override fun save(gathering: AdminGathering): AdminGathering =
		gatheringJpaRepository.save(gathering.toEntity()).toDomain()

	override fun findById(id: Long): AdminGatheringStatus? =
		gatheringJpaRepository.findById(id)
			.map { entity: GatheringEntity -> AdminGatheringStatus(id = entity.id ?: 0, status = entity.status) }
			.orElse(null)

	// 기존 행을 로드해 status를 [status]로 전이해 저장한다. (다른 필드 보존)
	override fun changeStatus(id: Long, status: GatheringStatus) {
		val entity: GatheringEntity = gatheringJpaRepository.findById(id)
			.orElseThrow { IllegalStateException("모임을 찾을 수 없습니다: $id") }
		entity.status = status
		gatheringJpaRepository.save(entity)
	}

	override fun loadById(id: Long): AdminGathering? =
		gatheringJpaRepository.findById(id)
			.map { entity: GatheringEntity -> entity.toDomain() }
			.orElse(null)

	// 기존 행을 로드해 데이터 필드를 [gathering] 값으로 덮어쓴다. (status·user_id·생성 시각은 보존)
	override fun update(gathering: AdminGathering) {
		val entity: GatheringEntity = gatheringJpaRepository.findById(gathering.id)
			.orElseThrow { IllegalStateException("모임을 찾을 수 없습니다: ${gathering.id}") }
		entity.type = gathering.type
		entity.title = gathering.title
		entity.description = gathering.description
		entity.imageKey = gathering.imageKey
		entity.region = gathering.region
		entity.minParticipants = gathering.minParticipants
		entity.maxParticipants = gathering.maxParticipants
		gatheringJpaRepository.save(entity)
	}
}
