package com.org.meeple.core.alarm.query.dto

import com.org.meeple.common.alarm.AlarmType
import java.time.LocalDateTime

/**
 * 알람 목록 조회 결과 한 건을 담는 읽기 모델(read model).
 * 커맨드 도메인([com.org.meeple.core.alarm.command.domain.Alarm])과 분리해, 조회 응답에 필요한 형태만 노출한다.
 * [link]는 알람을 눌렀을 때 이동할 대상 경로, [fromUserId]는 알람을 유발한 상대(없으면 null), [fromTeamId]는 알람을 유발한 상대 팀(없으면 null)이다.
 */
data class AlarmView(
	val id: Long,
	val type: AlarmType,
	val title: String,
	val description: String,
	val link: String,
	val fromUserId: Long?,
	val fromTeamId: Long?,
	val isRead: Boolean,
	val createdAt: LocalDateTime?,
)
