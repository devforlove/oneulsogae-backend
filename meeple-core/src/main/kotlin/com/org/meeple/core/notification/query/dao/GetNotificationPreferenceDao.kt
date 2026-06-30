package com.org.meeple.core.notification.query.dao

import com.org.meeple.core.notification.query.dto.NotificationPreferenceView

/** 알림 설정 조회 dao(query out-port). 없으면 null. */
interface GetNotificationPreferenceDao {
	fun findByUserId(userId: Long): NotificationPreferenceView?
}
