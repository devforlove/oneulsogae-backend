package com.org.oneulsogae.core.mission.query.dao

/** 미션 완료 기록 조회 dao. 목록의 완료 여부 표시에 쓴다. */
interface GetMissionCompletionDao {

	/** 사용자가 완료한 미션 id 집합. */
	fun findCompletedMissionIds(userId: Long): Set<Long>
}
