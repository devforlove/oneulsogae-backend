package com.org.meeple.scheduler.match.domain

/**
 * 이미 성사(MATCHED)된 매칭에 속한 사용자 ID 집합. (매칭의 남/녀 양쪽 ID를 모두 담는다)
 * 이미 매칭된 사용자는 신규 소개 대상도, 소개 상대 후보도 아니므로 풀 적재·대상 순회에서 제외하는 데 쓴다.
 */
data class MatchedUserIds(
	val values: Set<Long>,
) {
	val size: Int get() = values.size

	/** [userId]가 이미 성사된 매칭에 속해 있는지 여부. */
	fun contains(userId: Long): Boolean = userId in values

	/** [users] 중 이미 성사된 매칭에 속한 사용자를 제외한 나머지를 반환한다. */
	fun exclude(users: List<ActiveUser>): List<ActiveUser> = users.filterNot { user: ActiveUser -> contains(user.userId) }
}
