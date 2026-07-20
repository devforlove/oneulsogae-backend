package com.org.oneulsogae.core.notification.command.application.port.out

import com.org.oneulsogae.core.notification.command.domain.NotificationPreference

/** 사용자 알림 설정 저장(신규 INSERT / 기존 UPDATE). */
interface SaveNotificationPreferencePort {
	fun save(preference: NotificationPreference): NotificationPreference
}
