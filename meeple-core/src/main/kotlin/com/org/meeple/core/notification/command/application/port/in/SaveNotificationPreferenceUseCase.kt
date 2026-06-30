package com.org.meeple.core.notification.command.application.port.`in`

import com.org.meeple.core.notification.command.application.port.`in`.command.SaveNotificationPreferenceCommand

/** 사용자 알림 설정 6개 전체를 교체(upsert)한다. */
interface SaveNotificationPreferenceUseCase {
	fun save(command: SaveNotificationPreferenceCommand)
}
