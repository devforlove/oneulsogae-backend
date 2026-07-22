package com.org.oneulsogae.core.notification.command.application

import com.org.oneulsogae.core.notification.command.application.port.`in`.SaveNotificationPreferenceUseCase
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SaveNotificationPreferenceCommand
import com.org.oneulsogae.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.application.port.out.SaveNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SaveNotificationPreferenceUseCase] 구현. 7개 설정 전체를 교체(upsert)한다.
 * 기존 행이 있으면 그 id를 이어받아 UPDATE, 없으면 INSERT 한다(user_id UNIQUE 위반 방지).
 * lounge가 null(구버전 6필드 클라이언트)이면 기존 저장값을 유지한다(없으면 기본값 true).
 */
@Service
@Transactional
class SaveNotificationPreferenceService(
	private val getNotificationPreferencePort: GetNotificationPreferencePort,
	private val saveNotificationPreferencePort: SaveNotificationPreferencePort,
) : SaveNotificationPreferenceUseCase {

	override fun save(command: SaveNotificationPreferenceCommand) {
		val existing: NotificationPreference? = getNotificationPreferencePort.findByUserId(command.userId)
		saveNotificationPreferencePort.save(
			NotificationPreference(
				id = existing?.id ?: 0,
				userId = command.userId,
				push = command.push,
				oneToOne = command.oneToOne,
				lounge = command.lounge ?: existing?.lounge ?: true,
				meeting = command.meeting,
				team = command.team,
				message = command.message,
				marketing = command.marketing,
			),
		)
	}
}
