package com.org.oneulsogae.core.notification.query.dto

/**
 * 알림 설정 조회 read model. (GET 응답 출처)
 * query는 command 도메인을 참조하지 않으므로 기본값도 여기서 자체 보유한다(프론트 기본값과 동일).
 */
data class NotificationPreferenceView(
	val push: Boolean,
	val oneToOne: Boolean,
	val lounge: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
) {

	companion object {

		/** 설정 행이 없는 사용자의 기본값. */
		fun default(): NotificationPreferenceView =
			NotificationPreferenceView(
				push = true,
				oneToOne = true,
				lounge = true,
				meeting = true,
				team = true,
				message = true,
				marketing = false,
			)
	}
}
