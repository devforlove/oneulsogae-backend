package com.org.meeple.scheduler.solomatch.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.solomatch.query.dto.MatchableUser

/**
 * 일일 매칭의 인메모리 후보 풀. `(성별, 지역)` 버킷(최근 로그인 내림차순) + 가용 userId 집합으로 구성한다.
 * 매칭된 유저를 [remove]로 가용에서 빼면 이후 [freshCandidates]·[contains]에서 즉시 제외된다. (배치의 "꺼내고 빼기"를 한 곳에 응집)
 * 프레임워크에 의존하지 않는다.
 */
class MatchPool private constructor(
	private val bucketsByKey: Map<BucketKey, List<MatchableUser>>,
	private val regionsByGender: Map<Gender, Set<Long>>,
	private val available: MutableSet<Long>,
) {

	/** [gender]·[regionId] 버킷에서 아직 가용한 후보를 최근 로그인순으로 돌려준다. */
	fun freshCandidates(gender: Gender, regionId: Long): List<MatchableUser> =
		(bucketsByKey[BucketKey(gender, regionId)] ?: emptyList())
			.filter { user: MatchableUser -> user.userId in available }

	/** [gender] 후보가 (한 명이라도) 존재하는 지역 집합. (후보 없는 지역의 헛순회를 건너뛰는 데 쓴다) */
	fun regionsWith(gender: Gender): Set<Long> =
		regionsByGender[gender] ?: emptySet()

	/** 매칭된 [user]를 가용에서 제거한다. */
	fun remove(user: MatchableUser) {
		available.remove(user.userId)
	}

	/** [user]가 아직 가용한지(=이번 실행에서 아직 짝지어지지 않았는지). */
	fun contains(user: MatchableUser): Boolean =
		user.userId in available

	/** 배치 종료 후 아직 가용한(=끝까지 짝지어지지 않은) userId 전체. (오늘 소개를 못 받은 대상) */
	fun remainingUserIds(): Set<Long> =
		available.toSet()

	private data class BucketKey(val gender: Gender, val regionId: Long)

	companion object {

		/** 후보들을 (성별, 지역) 버킷(최근 로그인 내림차순)으로 묶어 풀을 만든다. */
		fun of(users: List<MatchableUser>): MatchPool {
			val bucketsByKey: Map<BucketKey, List<MatchableUser>> = users
				.sortedByDescending { user: MatchableUser -> user.lastLoginAt }
				.groupBy { user: MatchableUser -> BucketKey(user.gender, user.regionId) }
			// 성별별 "후보가 있는 지역" 집합을 미리 만들어, 후보 없는 지역의 헛순회를 O(1)로 건너뛴다.
			val regionsByGender: Map<Gender, Set<Long>> = bucketsByKey.keys
				.groupBy { key: BucketKey -> key.gender }
				.mapValues { (_, keys: List<BucketKey>) -> keys.mapTo(mutableSetOf()) { key: BucketKey -> key.regionId } }
			val available: MutableSet<Long> = users.mapTo(mutableSetOf()) { user: MatchableUser -> user.userId }
			return MatchPool(bucketsByKey, regionsByGender, available)
		}
	}
}
