package com.org.oneulsogae.core.alarm.command.application.port.out

import java.time.LocalDateTime

/** 알람 일괄 읽음 처리 아웃포트. (명령 경로) */
interface MarkAlarmsReadPort {

	/** [since](포함) 이후 생성된 사용자의 읽지 않은 알람을 모두 읽음 처리하고, 처리된 건수를 반환한다. */
	fun markAllReadByUserIdSince(userId: Long, since: LocalDateTime): Int
}
