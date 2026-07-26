package com.org.oneulsogae.core.mission.query.service

import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluators
import com.org.oneulsogae.core.mission.query.dao.GetMissionCompletionDao
import com.org.oneulsogae.core.mission.query.dao.GetMissionDao
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.core.mission.query.dto.MissionView
import com.org.oneulsogae.core.mission.query.dto.MissionViews
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetMissionsUseCase] 구현. (조회 전용)
 * 활성 미션 목록에 사용자별 상태를 얹는다: 완료 가드가 있으면 completed, 미완료면 평가자로 eligible을 판정한다.
 */
@Service
@Transactional(readOnly = true)
class GetMissionsService(
	private val getMissionDao: GetMissionDao,
	private val getMissionCompletionDao: GetMissionCompletionDao,
	private val missionEvaluators: MissionEvaluators,
) : GetMissionsUseCase {

	override fun getMissions(userId: Long): MissionViews {
		val completedMissionIds: Set<Long> = getMissionCompletionDao.findCompletedMissionIds(userId)
		return MissionViews(
			getMissionDao.findActiveMissions().map { mission: Mission ->
				val completed: Boolean = mission.id in completedMissionIds
				MissionView(
					missionId = mission.id,
					type = mission.type,
					title = mission.title,
					description = mission.description,
					rewardCoin = mission.rewardCoin,
					completed = completed,
					// 이미 완료면 자격 판정은 무의미하므로 평가자를 호출하지 않는다(미완료만 판정).
					eligible = if (completed) false else missionEvaluators.resolve(mission.type).isEligible(userId),
				)
			},
		)
	}
}
