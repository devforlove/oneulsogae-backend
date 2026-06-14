package com.org.meeple.core.alarm.application.port.out

import com.org.meeple.core.alarm.domain.Alarm

/** 알람 저장 아웃포트. */
interface SaveAlarmPort {

	fun save(alarm: Alarm): Alarm
}
