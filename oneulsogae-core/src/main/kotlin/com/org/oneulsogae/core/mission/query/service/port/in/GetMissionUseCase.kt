package com.org.oneulsogae.core.mission.query.service.port.`in`

import com.org.oneulsogae.core.mission.query.dto.Mission

/** 활성 미션 정의 단건 조회 인포트. (claim이 미션 정의를 로드하는 데 쓴다) */
interface GetMissionUseCase {

	/** 활성 미션을 id로 조회한다. 없거나 비활성이면 MISSION_NOT_FOUND를 던진다. */
	fun getById(missionId: Long): Mission
}
