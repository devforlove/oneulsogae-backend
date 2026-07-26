package com.org.oneulsogae.infra.mission.command.adapter

import com.org.oneulsogae.core.mission.command.application.port.out.SaveMissionCompletionPort
import com.org.oneulsogae.infra.mission.command.entity.MissionCompletionEntity
import com.org.oneulsogae.infra.mission.command.repository.MissionCompletionJpaRepository
import org.springframework.stereotype.Component

/**
 * [MissionCompletionEntity] command 영속성 어댑터. 완료 가드 저장([SaveMissionCompletionPort])을 구현한다.
 * (user_id, mission_id) 유니크 위반은 saveAndFlush 시점에 DataIntegrityViolationException으로 즉시 표면화한다(호출 서비스가 잡아 409 매핑).
 */
@Component
class MissionCompletionAdapter(
	private val missionCompletionJpaRepository: MissionCompletionJpaRepository,
) : SaveMissionCompletionPort {

	override fun save(userId: Long, missionId: Long, rewardedCoin: Int) {
		missionCompletionJpaRepository.saveAndFlush(
			MissionCompletionEntity(userId = userId, missionId = missionId, rewardedCoin = rewardedCoin),
		)
	}
}
