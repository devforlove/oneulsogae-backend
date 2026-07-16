package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.admin.gathering.command.application.port.out.ChangeGatheringScheduleStatusPort
import com.org.meeple.admin.gathering.command.application.port.out.LoadGatheringSchedulePort
import com.org.meeple.admin.gathering.command.application.port.out.SaveGatheringSchedulePort
import com.org.meeple.admin.gathering.command.application.port.out.RestoreGatheringMemberSeatPort
import com.org.meeple.admin.gathering.command.domain.GatheringSchedule
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.gathering.command.application.port.out.GetJoiningSchedulePort
import com.org.meeple.core.gathering.command.application.port.out.SaveJoiningSchedulePort
import com.org.meeple.core.gathering.command.domain.JoiningSchedule
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.meeple.infra.gathering.command.mapper.toDomain
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.GatheringScheduleJpaRepository
import org.springframework.stereotype.Component

/**
 * [GatheringScheduleEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 어드민의 저장([SaveGatheringSchedulePort])·로드([LoadGatheringSchedulePort])·상태 전이([ChangeGatheringScheduleStatusPort])와
 * core 참가 접수의 잠금 조회([GetJoiningSchedulePort])·여분 반영([SaveJoiningSchedulePort]) out-port를 함께 구현한다.
 */
@Component
class GatheringScheduleAdapter(
	private val gatheringScheduleJpaRepository: GatheringScheduleJpaRepository,
) : SaveGatheringSchedulePort,
	LoadGatheringSchedulePort,
	ChangeGatheringScheduleStatusPort,
	GetJoiningSchedulePort,
	SaveJoiningSchedulePort,
	RestoreGatheringMemberSeatPort {

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

	// 참가 접수용: 비관적 쓰기 락으로 잠근 일정을 core 도메인으로 투영한다.
	override fun getForUpdate(scheduleId: Long): JoiningSchedule? =
		gatheringScheduleJpaRepository.findByIdForUpdate(scheduleId)?.let { entity: GatheringScheduleEntity ->
			JoiningSchedule(
				id = checkNotNull(entity.id),
				gatheringId = entity.gatheringId,
				status = entity.status,
				maleFee = entity.maleFee,
				femaleFee = entity.femaleFee,
				maleRemaining = entity.maleRemaining,
				femaleRemaining = entity.femaleRemaining,
				earlyBirdRemaining = entity.earlyBirdRemaining,
				earlyBirdDiscountRate = entity.earlyBirdDiscountRate,
				discountMaleFee = entity.discountMaleFee,
				discountFemaleFee = entity.discountFemaleFee,
			)
		}

	// 접수로 차감된 여분만 반영한다. (같은 트랜잭션에서 잠근 행 — 다른 필드 보존)
	override fun save(schedule: JoiningSchedule) {
		val entity: GatheringScheduleEntity = gatheringScheduleJpaRepository.findById(schedule.id)
			.orElseThrow { IllegalStateException("모임 일정을 찾을 수 없습니다: ${schedule.id}") }
		entity.maleRemaining = schedule.maleRemaining
		entity.femaleRemaining = schedule.femaleRemaining
		entity.earlyBirdRemaining = schedule.earlyBirdRemaining
		gatheringScheduleJpaRepository.save(entity)
	}

	// 거절된 접수의 자리를 복원한다. 접수 차감과 같은 잠금 경로(FOR UPDATE)로 동시 접수와 직렬화한다.
	override fun restore(scheduleId: Long, gender: Gender, earlyBirdApplied: Boolean) {
		val entity: GatheringScheduleEntity = gatheringScheduleJpaRepository.findByIdForUpdate(scheduleId)
			?: throw IllegalStateException("모임 일정을 찾을 수 없습니다: $scheduleId")
		if (gender == Gender.MALE) entity.maleRemaining += 1 else entity.femaleRemaining += 1
		if (earlyBirdApplied) {
			entity.earlyBirdRemaining = (entity.earlyBirdRemaining ?: 0) + 1
		}
		gatheringScheduleJpaRepository.save(entity)
	}
}
