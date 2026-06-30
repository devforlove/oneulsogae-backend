package com.org.meeple.core.notification.command.application.port.`in`.command

import com.org.meeple.common.alarm.AlarmType

/** 알림톡 전송 시도 입력. type으로 카테고리를 정해 설정을 평가한다. */
data class SendAlarmTalkCommand(
	val userId: Long,
	val type: AlarmType,
	val title: String,
	val body: String,
)
