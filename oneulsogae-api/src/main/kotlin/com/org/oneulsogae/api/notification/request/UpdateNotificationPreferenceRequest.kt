package com.org.oneulsogae.api.notification.request

import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SaveNotificationPreferenceCommand

/**
 * 알림 설정 7개 전체 교체 요청.
 * lounge만 선택(생략 가능) — 셀소 분리 이전의 구버전 클라이언트(6필드 전송)가 저장해도
 * lounge 값이 true로 초기화되지 않고 기존 값이 유지된다. 나머지는 모두 필수.
 */
data class UpdateNotificationPreferenceRequest(
	val push: Boolean,
	val oneToOne: Boolean,
	val lounge: Boolean? = null,
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
			lounge = lounge,
			meeting = meeting,
			team = team,
			message = message,
			marketing = marketing,
		)
}
