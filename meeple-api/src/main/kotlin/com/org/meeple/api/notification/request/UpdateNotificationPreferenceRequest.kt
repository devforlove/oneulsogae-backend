package com.org.meeple.api.notification.request

import com.org.meeple.core.notification.command.application.port.`in`.command.SaveNotificationPreferenceCommand

/** 알림 설정 6개 전체 교체 요청. (모두 필수) */
data class UpdateNotificationPreferenceRequest(
	val push: Boolean,
	val oneToOne: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
) {

	fun toCommand(userId: Long): SaveNotificationPreferenceCommand =
		SaveNotificationPreferenceCommand(
			userId = userId,
			push = push,
			oneToOne = oneToOne,
			meeting = meeting,
			team = team,
			message = message,
			marketing = marketing,
		)
}
