package com.org.meeple.core.notification.query.service

import com.org.meeple.core.notification.query.dao.GetNotificationPreferenceDao
import com.org.meeple.core.notification.query.dto.NotificationPreferenceView
import com.org.meeple.core.notification.query.service.port.`in`.GetNotificationPreferenceUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetNotificationPreferenceUseCase] 구현. 설정 행이 없으면 기본값 View를 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetNotificationPreferenceService(
	private val getNotificationPreferenceDao: GetNotificationPreferenceDao,
) : GetNotificationPreferenceUseCase {

	override fun getByUserId(userId: Long): NotificationPreferenceView =
		getNotificationPreferenceDao.findByUserId(userId) ?: NotificationPreferenceView.default()
}
