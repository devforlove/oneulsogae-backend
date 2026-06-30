package com.org.meeple.infra.notification.command.adapter

import com.org.meeple.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.meeple.core.notification.command.application.port.out.SaveNotificationPreferencePort
import com.org.meeple.core.notification.command.domain.NotificationPreference
import com.org.meeple.infra.notification.command.mapper.toDomain
import com.org.meeple.infra.notification.command.mapper.toEntity
import com.org.meeple.infra.notification.command.repository.NotificationPreferenceJpaRepository
import org.springframework.stereotype.Component

/**
 * 알림 설정 명령 아웃포트([GetNotificationPreferencePort]·[SaveNotificationPreferencePort])의 JPA 구현. (한 엔티티 - 한 어댑터)
 * 엔티티/도메인 변환을 책임지며 외부에는 도메인 모델만 노출한다.
 */
@Component
class NotificationPreferenceAdapter(
	private val repository: NotificationPreferenceJpaRepository,
) : GetNotificationPreferencePort, SaveNotificationPreferencePort {

	override fun findByUserId(userId: Long): NotificationPreference? =
		repository.findByUserId(userId)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge).
	override fun save(preference: NotificationPreference): NotificationPreference =
		repository.save(preference.toEntity()).toDomain()
}
