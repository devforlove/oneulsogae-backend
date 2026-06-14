package com.org.meeple.api.alarm.response

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.core.alarm.domain.Alarm
import com.org.meeple.core.alarm.domain.Alarms
import java.time.LocalDateTime

/**
 * 알람 응답. 목록 조회 결과 한 건을 담는다.
 * [link]는 알람을 눌렀을 때 이동할 대상 경로, [fromUserId]는 알람을 유발한 상대(없으면 null)다.
 */
data class AlarmResponse(
	val id: Long,
	val type: AlarmType,
	val title: String,
	val description: String,
	val link: String,
	val fromUserId: Long?,
	val isRead: Boolean,
	val createdAt: LocalDateTime?,
) {
	companion object {
		fun of(alarm: Alarm): AlarmResponse =
			AlarmResponse(
				id = alarm.id,
				type = alarm.type,
				title = alarm.title,
				description = alarm.description,
				link = alarm.link,
				fromUserId = alarm.fromUserId,
				isRead = alarm.isRead,
				createdAt = alarm.createdAt,
			)

		/** 알람 목록을 응답 목록으로 변환한다. */
		fun listOf(alarms: Alarms): List<AlarmResponse> =
			alarms.values.map { of(it) }
	}
}
