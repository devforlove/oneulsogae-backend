package com.org.oneulsogae.core.alarm.query.dto

/**
 * 알람을 유발한 팀([AlarmView.fromTeamId])별 구성원 userId 묶음의 일급 컬렉션(first-class collection).
 * teamId 하나에 그 팀 구성원 userId들이 매핑된다. 알람의 fromTeamId로 froms에 채울 팀 구성원을 식별하는 데 쓴다.
 * 구성원 프로필 자체는 [AlarmFroms]가 userId로 보관하므로, 여기서는 (teamId → userId 목록) 매핑만 담는다.
 */
data class AlarmTeamMembers(
	val values: Map<Long, List<Long>>,
) {

	/** 모든 팀의 구성원 userId를 중복 없이 모은다. (발신 프로필 일괄 조회에 합산해 쓴다) */
	fun userIds(): Set<Long> = values.values.flatten().toSet()

	/** [teamId] 팀의 구성원 userId 목록. 없으면 빈 목록. */
	fun userIdsOf(teamId: Long): List<Long> = values[teamId] ?: emptyList()

	companion object {

		/** 빈 팀 구성원 묶음. */
		fun empty(): AlarmTeamMembers = AlarmTeamMembers(emptyMap())
	}
}
