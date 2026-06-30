package com.org.meeple.infra.notification.command.repository

import com.org.meeple.infra.notification.command.entity.NotificationPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 알림 설정 영속성 리포지토리. user_id UNIQUE라 단건 조회는 1행을 돌려준다.
 * 명령(Get·Save out-port)은 [com.org.meeple.infra.notification.command.adapter.NotificationPreferenceAdapter]가,
 * 조회 dao는 [com.org.meeple.infra.notification.query.GetNotificationPreferenceDaoImpl]가 각각 사용한다.
 */
interface NotificationPreferenceJpaRepository : JpaRepository<NotificationPreferenceEntity, Long> {
	fun findByUserId(userId: Long): NotificationPreferenceEntity?
}
