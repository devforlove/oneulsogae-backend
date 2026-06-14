package com.org.meeple.core.alarm.application.port.`in`

import com.org.meeple.core.alarm.domain.Alarms

/** 내 알람 목록 조회 인포트(유스케이스). */
interface GetAlarmsUseCase {

	/** 사용자의 알람을 최신순으로 조회한다. */
	fun getAlarms(userId: Long): Alarms
}
