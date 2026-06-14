package com.org.meeple.scheduler.match.domain

import com.org.meeple.common.user.Gender
import kotlin.random.Random

/**
 * (성별, 지역=regionCode)을 키로 묶은 매칭 후보 풀 그룹.
 * 매칭은 반대 성별·같은 권역에서 이뤄지므로, 이 단위로 후보 풀을 적재해 두면 소개 시 빠르게 풀을 참조할 수 있다.
 */
data class MatchPoolGroup(
	val gender: Gender,
	val regionCode: Int,
	val userIds: List<Long>,
) {

	/**
	 * 후보 순서를 무작위로 섞은 새 그룹을 반환한다. (적재 순서의 편향을 없애기 위함)
	 * 무작위 소스([random])는 파라미터로 받아 도메인이 인프라/난수 구현에 직접 의존하지 않게 한다.
	 */
	fun shuffled(random: Random = Random.Default): MatchPoolGroup =
		copy(userIds = userIds.shuffled(random))

	companion object {

		/** 활성 사용자들을 (성별, 지역) 기준으로 묶어 그룹 목록으로 만든다. */
		fun group(activeUsers: List<ActiveUser>): List<MatchPoolGroup> =
			activeUsers
				.groupBy({ user: ActiveUser -> user.gender to user.regionCode }, { user: ActiveUser -> user.userId })
				.map { (key: Pair<Gender, Int>, userIds: List<Long>) ->
					MatchPoolGroup(gender = key.first, regionCode = key.second, userIds = userIds)
				}
	}
}
