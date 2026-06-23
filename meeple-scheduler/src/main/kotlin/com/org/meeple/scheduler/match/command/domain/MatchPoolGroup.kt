package com.org.meeple.scheduler.match.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.query.dto.ActiveUser
import kotlin.random.Random

/**
 * (성별, 지역=regionId)을 키로 묶은 매칭 후보 풀 그룹.
 * 매칭은 반대 성별·가까운 지역 순으로 이뤄지므로, 지역별로 풀을 적재해 두면 소개 시 근접 지역 풀을 차례로 참조할 수 있다.
 */
data class MatchPoolGroup(
	val gender: Gender,
	val regionId: Long,
	val userIds: List<Long>,
) {

	/**
	 * 후보 순서를 무작위로 섞은 새 그룹을 반환한다. (같은 지역 내 적재 순서 편향 제거)
	 * 무작위 소스([random])는 파라미터로 받아 도메인이 인프라/난수 구현에 직접 의존하지 않게 한다.
	 */
	fun shuffled(random: Random = Random.Default): MatchPoolGroup =
		copy(userIds = userIds.shuffled(random))

	companion object {

		/** 활성 사용자들을 (성별, 지역) 기준으로 묶어 그룹 목록으로 만든다. */
		fun group(activeUsers: List<ActiveUser>): List<MatchPoolGroup> =
			activeUsers
				.groupBy({ user: ActiveUser -> user.gender to user.regionId }, { user: ActiveUser -> user.userId })
				.map { (key: Pair<Gender, Long>, userIds: List<Long>) ->
					MatchPoolGroup(gender = key.first, regionId = key.second, userIds = userIds)
				}
	}
}
