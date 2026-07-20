package com.org.oneulsogae.infra.notification.query

import com.org.oneulsogae.core.notification.query.dao.GetNotificationPreferenceDao
import com.org.oneulsogae.core.notification.query.dto.NotificationPreferenceView
import com.org.oneulsogae.infra.notification.command.repository.NotificationPreferenceJpaRepository
import org.springframework.stereotype.Component

/**
 * 알림 설정 조회 dao([GetNotificationPreferenceDao]) 구현. (조회 전용 read model 투영)
 * user_id UNIQUE 인덱스로 단건 seek. 명령 도메인(NotificationPreference) 대신 View로 직접 투영한다.
 */
@Component
class GetNotificationPreferenceDaoImpl(
	private val repository: NotificationPreferenceJpaRepository,
) : GetNotificationPreferenceDao {

	override fun findByUserId(userId: Long): NotificationPreferenceView? =
		repository.findByUserId(userId)?.let { entity ->
			NotificationPreferenceView(
				push = entity.push,
				oneToOne = entity.oneToOne,
				meeting = entity.meeting,
				team = entity.team,
				message = entity.message,
				marketing = entity.marketing,
			)
		}
}
