package com.org.oneulsogae.api.alarm.response

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.core.alarm.query.dto.AlarmFrom
import com.org.oneulsogae.core.alarm.query.dto.AlarmFroms
import com.org.oneulsogae.core.alarm.query.dto.AlarmTeamMembers
import com.org.oneulsogae.core.alarm.query.dto.AlarmView
import com.org.oneulsogae.core.alarm.query.dto.AlarmViews
import com.org.oneulsogae.core.alarm.query.dto.AlarmsResult
import java.time.LocalDateTime

/**
 * 알람 응답. 목록 조회 결과 한 건을 담는다.
 * [link]는 알람을 눌렀을 때 이동할 대상 경로, [fromUserId]는 알람을 유발한 상대(없으면 null), [fromTeamId]는 알람을 유발한 상대 팀(없으면 null)이다.
 * [froms]는 이 알람을 보낸 사람들의 표시용 프로필(프로필 이미지·성별)이다.
 * fromTeamId가 있으면 그 팀 구성원들이, fromUserId가 있으면 그 발신 유저 한 명이 들어간다. (없거나 프로필이 없으면 빈 배열)
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
		private fun of(
			alarm: AlarmView,
			fromByUserId: Map<Long, AlarmFromResponse>,
			teamMembers: AlarmTeamMembers,
		): AlarmResponse =
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
				froms = resolveFroms(alarm, fromByUserId, teamMembers),
			)

		// 알람의 발신자 프로필을 붙인다. fromTeamId가 있으면 그 팀 구성원들, 없고 fromUserId가 있으면 그 한 명. (프로필 없는 id는 제외)
		private fun resolveFroms(
			alarm: AlarmView,
			fromByUserId: Map<Long, AlarmFromResponse>,
			teamMembers: AlarmTeamMembers,
		): List<AlarmFromResponse> {
			val fromTeamId: Long? = alarm.fromTeamId
			val fromUserId: Long? = alarm.fromUserId
			return when {
				fromTeamId != null -> teamMembers.userIdsOf(fromTeamId).mapNotNull { fromByUserId[it] }
				fromUserId != null -> fromByUserId[fromUserId]?.let { listOf(it) } ?: emptyList()
				else -> emptyList()
			}
		}

		/**
		 * 알람 목록을 응답 목록으로 변환한다.
		 * 발신 유저 프로필([AlarmsResult.froms])을 userId로 색인하고, 팀 구성원 매핑([AlarmsResult.teamMembers])과 함께 각 알람의 [froms]에 매핑한다.
		 * (IN 조회 1회로 모은 결과를 재사용)
		 */
		fun listOf(result: AlarmsResult): List<AlarmResponse> {
			val fromByUserId: Map<Long, AlarmFromResponse> = result.froms.toResponseByUserId()
			return result.alarms.toResponses(fromByUserId, result.teamMembers)
		}

		// 발신 유저 프로필을 userId로 색인해 응답으로 변환한다. (수신자 자신의 목록만 순회 — values 노출 캡슐화)
		private fun AlarmFroms.toResponseByUserId(): Map<Long, AlarmFromResponse> =
			values.associate { from: AlarmFrom -> from.userId to AlarmFromResponse.of(from) }

		// 알람 목록을 응답 목록으로 변환한다. (수신자 자신의 목록만 순회 — values 노출 캡슐화)
		private fun AlarmViews.toResponses(
			fromByUserId: Map<Long, AlarmFromResponse>,
			teamMembers: AlarmTeamMembers,
		): List<AlarmResponse> =
			values.map { alarm: AlarmView -> of(alarm, fromByUserId, teamMembers) }
	}
}
