package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.admin.gathering.command.application.port.out.ChangeGatheringScheduleStatusPort
import com.org.meeple.admin.gathering.command.application.port.out.LoadGatheringSchedulePort
import com.org.meeple.admin.gathering.command.application.port.out.SaveGatheringSchedulePort
import com.org.meeple.admin.gathering.command.domain.GatheringSchedule
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.meeple.infra.gathering.command.mapper.toDomain
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.GatheringScheduleJpaRepository
import org.springframework.stereotype.Component

/**
 * [GatheringScheduleEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 저장([SaveGatheringSchedulePort])·로드([LoadGatheringSchedulePort])·상태 전이([ChangeGatheringScheduleStatusPort]) out-port를 함께 구현한다.
 */
@Component
class GatheringScheduleAdapter(
	private val gatheringScheduleJpaRepository: GatheringScheduleJpaRepository,
) : SaveGatheringSchedulePort,
	LoadGatheringSchedulePort,
	ChangeGatheringScheduleStatusPort {

	override fun save(schedule: GatheringSchedule): GatheringSchedule =
		gatheringScheduleJpaRepository.save(schedule.toEntity()).toDomain()

	override fun loadById(id: Long): GatheringSchedule? =
		gatheringScheduleJpaRepository.findById(id)
			.map { entity: GatheringScheduleEntity -> entity.toDomain() }
			.orElse(null)

	// 기존 행을 로드해 status를 [status]로 전이해 저장한다. (시간 범위 등 다른 필드 보존)
	override fun changeStatus(id: Long, status: GatheringScheduleStatus) {
		val entity: GatheringScheduleEntity = gatheringScheduleJpaRepository.findById(id)
			.orElseThrow { IllegalStateException("모임 일정을 찾을 수 없습니다: $id") }
		entity.status = status
		gatheringScheduleJpaRepository.save(entity)
	}
}
