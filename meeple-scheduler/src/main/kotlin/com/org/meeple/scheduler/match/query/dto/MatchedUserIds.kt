package com.org.meeple.scheduler.match.query.dto

/**
 * 이미 성사(MATCHED)된 매칭에 속한 사용자 ID 집합. 신규 소개 대상·후보에서 제외하는 데 쓴다.
 */
data class MatchedUserIds(
	val values: Set<Long>,
) {
	val size: Int get() = values.size

	/** [userId]가 이미 성사된 매칭에 속해 있는지 여부. */
	fun contains(userId: Long): Boolean = userId in values
}
