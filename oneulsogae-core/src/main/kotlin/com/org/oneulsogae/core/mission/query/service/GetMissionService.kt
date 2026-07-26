package com.org.oneulsogae.core.mission.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.mission.MissionErrorCode
import com.org.oneulsogae.core.mission.query.dao.GetMissionDao
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetMissionUseCase] 구현. 활성 미션을 조회하고 없으면 [MissionErrorCode.MISSION_NOT_FOUND]. */
@Service
@Transactional(readOnly = true)
class GetMissionService(
	private val getMissionDao: GetMissionDao,
) : GetMissionUseCase {

	override fun getById(missionId: Long): Mission =
		getMissionDao.findActiveById(missionId)
			?: throw BusinessException(MissionErrorCode.MISSION_NOT_FOUND)
}
