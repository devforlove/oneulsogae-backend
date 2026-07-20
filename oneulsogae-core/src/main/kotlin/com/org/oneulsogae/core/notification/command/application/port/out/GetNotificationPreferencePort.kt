package com.org.oneulsogae.core.notification.command.application.port.out

import com.org.oneulsogae.core.notification.command.domain.NotificationPreference

/** 사용자 알림 설정 단건 조회. (upsert·게이트용. 없으면 null) */
interface GetNotificationPreferencePort {
	fun findByUserId(userId: Long): NotificationPreference?
}
