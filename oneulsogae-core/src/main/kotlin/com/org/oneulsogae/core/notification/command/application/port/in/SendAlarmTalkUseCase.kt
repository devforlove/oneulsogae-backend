package com.org.oneulsogae.core.notification.command.application.port.`in`

import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand

/**
 * 사용자 설정을 확인해 알림톡 전송을 시도한다.
 * 설정이 허용하지 않으면(혹은 push가 꺼져 있으면) 아무것도 보내지 않는다. (게이트)
 */
interface SendAlarmTalkUseCase {
	fun attempt(command: SendAlarmTalkCommand)
}
