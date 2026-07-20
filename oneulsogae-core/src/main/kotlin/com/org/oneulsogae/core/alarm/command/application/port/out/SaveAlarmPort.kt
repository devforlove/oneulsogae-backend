package com.org.oneulsogae.core.alarm.command.application.port.out

import com.org.oneulsogae.core.alarm.command.domain.Alarm

/** 알람 저장 아웃포트. */
interface SaveAlarmPort {

	fun save(alarm: Alarm): Alarm
}
