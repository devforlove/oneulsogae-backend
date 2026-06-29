package com.org.meeple.core.alarm.query.service.port.`in`

/** 미수신(읽지 않은) 알람 개수 조회 인포트(유스케이스). */
interface GetUnreadAlarmCountUseCase {

	/** 사용자의 읽지 않은 알람 개수를 조회한다. (알람 목록과 동일한 최근 보관 기간 기준) */
	fun getUnreadCount(userId: Long): Long
}
