package com.org.oneulsogae.core.notification.command.domain

import com.org.oneulsogae.common.notification.NotificationCategory

/**
 * 사용자별 알림 설정. push 마스터 스위치 + 카테고리별 on/off를 보관한다.
 * 알림톡 전송 게이트는 [allows]로 'push && 해당 카테고리'를 판정한다(서비스에 if 나열 금지).
 * 행이 없는 사용자는 [default]로 간주한다(프론트 DEFAULT_NOTIFICATIONS와 일치).
 */
data class NotificationPreference(
	val id: Long = 0,
	val userId: Long,
	val push: Boolean = true,
	val oneToOne: Boolean = true,
	val meeting: Boolean = true,
	val team: Boolean = true,
	val message: Boolean = true,
	val marketing: Boolean = false,
) {

	/** push 마스터가 켜져 있고 [category] 플래그도 켜져 있을 때만 true. */
	fun allows(category: NotificationCategory): Boolean =
		push && when (category) {
			NotificationCategory.ONE_TO_ONE -> oneToOne
			NotificationCategory.MEETING -> meeting
			NotificationCategory.TEAM -> team
			NotificationCategory.MESSAGE -> message
			NotificationCategory.MARKETING -> marketing
			// COIN은 인앱 전용이라 알림톡 push를 보내지 않는다(토글 없음). 항상 false로 게이트한다.
			NotificationCategory.COIN -> false
		}

	companion object {

		/** 설정 행이 없는 사용자의 기본값. (프론트 DEFAULT_NOTIFICATIONS와 동일) */
		fun default(userId: Long): NotificationPreference = NotificationPreference(userId = userId)
	}
}
