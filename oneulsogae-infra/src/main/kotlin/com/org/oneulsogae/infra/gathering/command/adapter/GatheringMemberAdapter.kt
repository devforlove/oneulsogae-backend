package com.org.oneulsogae.infra.gathering.command.adapter

import com.org.oneulsogae.admin.gathering.command.application.port.out.ChangeGatheringMemberStatusPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadAdminGatheringMemberPort
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.core.gathering.command.application.port.out.LoadGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.mapper.toDomain
import com.org.oneulsogae.infra.gathering.command.mapper.toEntity
import com.org.oneulsogae.infra.gathering.command.repository.GatheringMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * [GatheringMemberEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * core 참가 접수의 조회([LoadGatheringMemberPort])·저장([SaveGatheringMemberPort])과
 * 어드민 승인/거절의 조회([LoadAdminGatheringMemberPort])·상태 전이([ChangeGatheringMemberStatusPort]) out-port를 함께 구현한다.
 */
@Component
class GatheringMemberAdapter(
	private val gatheringMemberJpaRepository: GatheringMemberJpaRepository,
) : LoadGatheringMemberPort,
	SaveGatheringMemberPort,
	LoadAdminGatheringMemberPort,
	ChangeGatheringMemberStatusPort {

	override fun loadByScheduleIdAndUserId(scheduleId: Long, userId: Long): GatheringMember? =
		gatheringMemberJpaRepository.findByScheduleIdAndUserId(scheduleId, userId)?.toDomain()

	// 신규(id null)는 insert, 기존 행은 로드해 상태·성별·얼리버드 적용 여부를 갱신한다. (gathering_id 등 식별 필드 보존)
	override fun save(member: GatheringMember): GatheringMember {
		val memberId: Long? = member.id
		if (memberId == null) {
			return gatheringMemberJpaRepository.save(member.toEntity()).toDomain()
		}
		val entity: GatheringMemberEntity = gatheringMemberJpaRepository.findById(memberId)
			.orElseThrow { IllegalStateException("모임 참가자를 찾을 수 없습니다: $memberId") }
		entity.status = member.status
		entity.gender = member.gender
		entity.earlyBirdApplied = member.earlyBirdApplied
		return gatheringMemberJpaRepository.save(entity).toDomain()
	}

	// 어드민 승인/거절용: 참가 행을 admin 도메인으로 투영한다.
	override fun loadById(memberId: Long): AdminGatheringMember? =
		gatheringMemberJpaRepository.findById(memberId)
			.map { entity: GatheringMemberEntity ->
				AdminGatheringMember(
					id = checkNotNull(entity.id),
					userId = entity.userId,
					scheduleId = entity.scheduleId,
					gender = entity.gender,
					status = entity.status,
					earlyBirdApplied = entity.earlyBirdApplied,
				)
			}
			.orElse(null)

	// 기존 행을 로드해 status를 [status]로 전이해 저장한다. (다른 필드 보존)
	override fun changeStatus(memberId: Long, status: GatheringMemberStatus) {
		val entity: GatheringMemberEntity = gatheringMemberJpaRepository.findById(memberId)
			.orElseThrow { IllegalStateException("모임 참가자를 찾을 수 없습니다: $memberId") }
		entity.status = status
		gatheringMemberJpaRepository.save(entity)
	}
}
