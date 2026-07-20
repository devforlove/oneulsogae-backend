package com.org.oneulsogae.core.alarm.command.application.port.`in`.command

import com.org.oneulsogae.common.alarm.AlarmType

/**
 * 알람 저장 입력.
 * [userId]는 알람 수신자, [title]/[description]은 노출 문구, [link]는 눌렀을 때 이동할 경로,
 * [fromUserId]는 알람을 유발한 상대(없으면 null), [fromTeamId]는 알람을 유발한 상대 팀(없으면 null)이다.
 */
data class SaveAlarmCommand(
	val userId: Long,
	val type: AlarmType,
	val title: String,
	val description: String,
	val link: String,
	val fromUserId: Long? = null,
	val fromTeamId: Long? = null,
)
