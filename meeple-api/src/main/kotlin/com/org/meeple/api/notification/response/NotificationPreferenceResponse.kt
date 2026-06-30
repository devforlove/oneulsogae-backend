package com.org.meeple.api.notification.response

import com.org.meeple.core.notification.query.dto.NotificationPreferenceView

/** 알림 설정 조회 응답. */
data class NotificationPreferenceResponse(
	val push: Boolean,
	val oneToOne: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
) {

	companion object {

		fun from(view: NotificationPreferenceView): NotificationPreferenceResponse =
			NotificationPreferenceResponse(
				push = view.push,
				oneToOne = view.oneToOne,
				meeting = view.meeting,
				team = view.team,
				message = view.message,
				marketing = view.marketing,
			)
	}
}
