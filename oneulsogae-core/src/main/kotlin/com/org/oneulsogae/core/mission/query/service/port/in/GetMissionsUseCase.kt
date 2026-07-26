package com.org.oneulsogae.core.mission.query.service.port.`in`

import com.org.oneulsogae.core.mission.query.dto.MissionViews

/** 사용자별 미션 목록(정의 + 완료·수령가능 상태) 조회 인포트. */
interface GetMissionsUseCase {

	fun getMissions(userId: Long): MissionViews
}
