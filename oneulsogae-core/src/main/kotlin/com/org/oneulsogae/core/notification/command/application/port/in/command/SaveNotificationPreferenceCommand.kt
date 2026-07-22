package com.org.oneulsogae.core.notification.command.application.port.`in`.command

/**
 * 알림 설정 7개 전체 교체 입력. (full replace)
 * [lounge]는 셀소 분리 이전의 구버전 클라이언트(6필드 전송) 호환을 위해 nullable —
 * null이면 서비스가 기존 저장값(없으면 기본값 true)을 유지한다.
 */
data class SaveNotificationPreferenceCommand(
	val userId: Long,
	val push: Boolean,
	val oneToOne: Boolean,
	val lounge: Boolean?,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
)
