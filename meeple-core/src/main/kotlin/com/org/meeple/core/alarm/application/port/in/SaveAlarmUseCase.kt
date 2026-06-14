package com.org.meeple.core.alarm.application.port.`in`

import com.org.meeple.core.alarm.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.core.alarm.domain.Alarm

/**
 * 알람 저장 인포트(유스케이스).
 * 다른 도메인은 이 in-port를 주입해 알람을 남긴다. (out-port 직접 의존 금지)
 */
interface SaveAlarmUseCase {

	fun save(command: SaveAlarmCommand): Alarm
}
