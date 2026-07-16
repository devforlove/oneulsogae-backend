package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.core.gathering.command.application.port.out.LoadGatheringMemberPort
import com.org.meeple.core.gathering.command.application.port.out.SaveGatheringMemberPort
import com.org.meeple.core.gathering.command.domain.GatheringMember
import com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity
import com.org.meeple.infra.gathering.command.mapper.toDomain
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.GatheringMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * [GatheringMemberEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * core 참가 접수의 조회([LoadGatheringMemberPort])·저장([SaveGatheringMemberPort]) out-port를 구현한다.
 */
@Component
class GatheringMemberAdapter(
	private val gatheringMemberJpaRepository: GatheringMemberJpaRepository,
) : LoadGatheringMemberPort,
	SaveGatheringMemberPort {

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
}
