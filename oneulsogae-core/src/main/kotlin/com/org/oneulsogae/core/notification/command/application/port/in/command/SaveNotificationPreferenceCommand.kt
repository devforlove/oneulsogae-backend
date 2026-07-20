package com.org.oneulsogae.core.notification.command.application.port.`in`.command

/** 알림 설정 6개 전체 교체 입력. (full replace) */
data class SaveNotificationPreferenceCommand(
	val userId: Long,
	val push: Boolean,
	val oneToOne: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
)
