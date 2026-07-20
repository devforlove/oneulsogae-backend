package com.org.oneulsogae.core.notification.query.service.port.`in`

import com.org.oneulsogae.core.notification.query.dto.NotificationPreferenceView

/** 사용자 알림 설정 조회. 행이 없으면 기본값 View를 반환한다. */
interface GetNotificationPreferenceUseCase {
	fun getByUserId(userId: Long): NotificationPreferenceView
}
