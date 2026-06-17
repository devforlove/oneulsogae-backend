package com.org.meeple.core.alarm.query.dao

import com.org.meeple.core.alarm.query.dto.AlarmFroms

/**
 * 알람 발신 유저 프로필 조회 dao(query out-port 인터페이스). (조회 전용 read model 반환)
 * 알람을 유발한 발신 유저들의 표시용 프로필을 한 번의 IN 조회로 가져와 [AlarmFroms]로 투영한다.
 * 프로필 상세는 user 도메인이 소유하므로, 실제 구현(infra 읽기 dao)이 user_details를 조인해 채운다.
 */
interface GetAlarmFromDao {

	/** [userIds]에 해당하는 발신 유저들의 프로필을 IN 조회로 한 번에 가져온다. (없으면 빈 [AlarmFroms]) */
	fun findByUserIds(userIds: Set<Long>): AlarmFroms
}
