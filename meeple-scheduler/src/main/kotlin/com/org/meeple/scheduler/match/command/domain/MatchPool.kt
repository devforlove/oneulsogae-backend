package com.org.meeple.scheduler.match.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.query.dto.MatchableUser

/**
 * 일일 매칭의 인메모리 후보 풀. `(성별, 지역)` 버킷(최근 로그인 내림차순) + 가용 userId 집합으로 구성한다.
 * 매칭된 유저를 [remove]로 가용에서 빼면 이후 [freshCandidates]·[contains]에서 즉시 제외된다. (배치의 "꺼내고 빼기"를 한 곳에 응집)
 * 프레임워크에 의존하지 않는다.
 */
class MatchPool private constructor(
	private val bucketsByKey: Map<BucketKey, List<MatchableUser>>,
	private val available: MutableSet<Long>,
) {

	/** [gender]·[regionId] 버킷에서 아직 가용한 후보를 최근 로그인순으로 돌려준다. */
	fun freshCandidates(gender: Gender, regionId: Long): List<MatchableUser> =
		(bucketsByKey[BucketKey(gender, regionId)] ?: emptyList())
			.filter { user: MatchableUser -> user.userId in available }

	/** 매칭된 [user]를 가용에서 제거한다. */
	fun remove(user: MatchableUser) {
		available.remove(user.userId)
	}

	/** [user]가 아직 가용한지(=이번 실행에서 아직 짝지어지지 않았는지). */
	fun contains(user: MatchableUser): Boolean =
		user.userId in available

	private data class BucketKey(val gender: Gender, val regionId: Long)

	companion object {

		/** 후보들을 (성별, 지역) 버킷(최근 로그인 내림차순)으로 묶어 풀을 만든다. */
		fun of(users: List<MatchableUser>): MatchPool {
			val bucketsByKey: Map<BucketKey, List<MatchableUser>> = users
				.sortedByDescending { user: MatchableUser -> user.lastLoginAt }
				.groupBy { user: MatchableUser -> BucketKey(user.gender, user.regionId) }
			val available: MutableSet<Long> = users.mapTo(mutableSetOf()) { user: MatchableUser -> user.userId }
			return MatchPool(bucketsByKey, available)
		}
	}
}
