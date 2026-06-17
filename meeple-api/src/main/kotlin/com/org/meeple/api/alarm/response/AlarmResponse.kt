package com.org.meeple.api.alarm.response

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.core.alarm.query.dto.AlarmView
import com.org.meeple.core.alarm.query.dto.AlarmsResult
import java.time.LocalDateTime

/**
 * 알람 응답. 목록 조회 결과 한 건을 담는다.
 * [link]는 알람을 눌렀을 때 이동할 대상 경로, [fromUserId]는 알람을 유발한 상대(없으면 null), [fromTeamId]는 알람을 유발한 상대 팀(없으면 null)이다.
 * [froms]는 이 알람을 보낸 사람들의 표시용 프로필(프로필 이미지·성별)이다. (발신자가 없거나 프로필이 없으면 빈 배열)
 */
data class AlarmResponse(
	val id: Long,
	val type: AlarmType,
	val title: String,
	val description: String,
	val link: String,
	val fromUserId: Long?,
	val fromTeamId: Long?,
	val isRead: Boolean,
	val createdAt: LocalDateTime?,
	val froms: List<AlarmFromResponse>,
) {
	companion object {
		private fun of(alarm: AlarmView, fromByUserId: Map<Long, AlarmFromResponse>): AlarmResponse =
			AlarmResponse(
				id = alarm.id,
				type = alarm.type,
				title = alarm.title,
				description = alarm.description,
				link = alarm.link,
				fromUserId = alarm.fromUserId,
				fromTeamId = alarm.fromTeamId,
				isRead = alarm.isRead,
				createdAt = alarm.createdAt,
				// 알람의 발신자(fromUserId)에 해당하는 프로필을 붙인다. (없으면 빈 배열)
				froms = alarm.fromUserId?.let { fromByUserId[it] }?.let { listOf(it) } ?: emptyList(),
			)

		/**
		 * 알람 목록을 응답 목록으로 변환한다.
		 * 발신 유저 프로필([AlarmsResult.froms])을 userId로 색인해 각 알람의 [froms]에 매핑한다. (IN 조회 1회로 모은 결과를 재사용)
		 */
		fun listOf(result: AlarmsResult): List<AlarmResponse> {
			val fromByUserId: Map<Long, AlarmFromResponse> =
				result.froms.values.associate { it.userId to AlarmFromResponse.of(it) }
			return result.alarms.values.map { of(it, fromByUserId) }
		}
	}
}
