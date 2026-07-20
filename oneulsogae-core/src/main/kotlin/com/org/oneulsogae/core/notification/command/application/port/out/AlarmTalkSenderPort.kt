package com.org.oneulsogae.core.notification.command.application.port.out

/**
 * 카카오 알림톡 전송 아웃포트. 현재 구현은 로그만 남기는 stub이며,
 * 나중에 이 구현만 실제 카카오 API 호출로 교체한다(포트·게이트는 그대로).
 */
interface AlarmTalkSenderPort {
	fun send(userId: Long, title: String, body: String)
}
