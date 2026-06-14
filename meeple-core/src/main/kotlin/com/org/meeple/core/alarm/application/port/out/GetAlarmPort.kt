package com.org.meeple.core.alarm.application.port.out

import com.org.meeple.core.alarm.domain.Alarms
import java.time.LocalDateTime

/** 알람 조회 아웃포트. */
interface GetAlarmPort {

	/** [since](포함) 이후 생성된 사용자의 알람을 생성 시각 최신순으로 조회한다. (없으면 빈 [Alarms]) */
	fun findByUserIdSince(userId: Long, since: LocalDateTime): Alarms
}
