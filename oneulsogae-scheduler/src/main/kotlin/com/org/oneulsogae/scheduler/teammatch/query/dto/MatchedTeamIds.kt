package com.org.oneulsogae.scheduler.teammatch.query.dto

/**
 * 성사(MATCHED)된 팀 매칭에 속한 팀 ID 전체를 담는 일급 컬렉션. (1:1 [MatchedUserIds]의 팀 버전)
 * 배치 시작 시 한 번 적재해, 풀 적재·대상 순회에서 이미 성사된 팀을 제외하는 데 쓴다.
 */
data class MatchedTeamIds(val values: Set<Long>) {

	val size: Int
		get() = values.size

	fun contains(teamId: Long): Boolean =
		teamId in values
}
