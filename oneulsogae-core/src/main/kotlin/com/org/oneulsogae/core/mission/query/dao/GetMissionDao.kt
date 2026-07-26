package com.org.oneulsogae.core.mission.query.dao

import com.org.oneulsogae.core.mission.query.dto.Mission

/** 미션 정의 조회 dao. 활성(active·미삭제) 미션만 반환한다. */
interface GetMissionDao {

	/** 활성 미션 전체를 노출 순서(display_order 오름차순)로 조회한다. */
	fun findActiveMissions(): List<Mission>

	/** 활성 미션 한 건을 id로 조회한다. 없거나 비활성이면 null. */
	fun findActiveById(missionId: Long): Mission?
}
