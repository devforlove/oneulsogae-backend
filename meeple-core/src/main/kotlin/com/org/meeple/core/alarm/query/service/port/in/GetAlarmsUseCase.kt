package com.org.meeple.core.alarm.query.service.port.`in`

import com.org.meeple.core.alarm.query.dto.AlarmsResult

/** 내 알람 목록 조회 인포트(유스케이스). */
interface GetAlarmsUseCase {

	/** 사용자의 알람을 최신순으로 조회하고, 발신 유저 프로필을 함께 담아 반환한다. */
	fun getAlarms(userId: Long): AlarmsResult
}
