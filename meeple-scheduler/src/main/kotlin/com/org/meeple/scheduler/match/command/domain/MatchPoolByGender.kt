package com.org.meeple.scheduler.match.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.query.dto.ActiveUser
import kotlin.random.Random

/**
 * 지역과 무관하게 성별만으로 묶은 매칭 후보 풀.
 * (성별, 지역) 풀([MatchPoolGroup])과 별개로, 같은 권역 후보가 마른 경우 등을 위해 성별 전체 풀을 따로 적재해 둔다.
 */
data class MatchPoolByGender(
	val gender: Gender,
	val userIds: List<Long>,
) {

	/**
	 * 후보 순서를 무작위로 섞은 새 풀을 반환한다. (적재 순서의 편향을 없애기 위함)
	 * 무작위 소스([random])는 파라미터로 받아 도메인이 인프라/난수 구현에 직접 의존하지 않게 한다.
	 */
	fun shuffled(random: Random = Random.Default): MatchPoolByGender =
		copy(userIds = userIds.shuffled(random))

	companion object {

		/** 활성 사용자들을 성별 기준으로만 묶어 풀 목록으로 만든다. (지역은 무시) */
		fun group(activeUsers: List<ActiveUser>): List<MatchPoolByGender> =
			activeUsers
				.groupBy({ user: ActiveUser -> user.gender }, { user: ActiveUser -> user.userId })
				.map { (gender: Gender, userIds: List<Long>) ->
					MatchPoolByGender(gender = gender, userIds = userIds)
				}
	}
}
