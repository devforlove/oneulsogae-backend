package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import java.time.LocalDate

/**
 * 미팅탭에 노출할 추천 팀 응답. 팀 메타와 팀원 표시 프로필(나이는 생일+오늘로 산출)을 담는다.
 */
data class RecommendedTeamResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val members: List<Member>,
) {

	/** 추천 팀 구성원 항목. */
	data class Member(
		val userId: Long,
		val nickname: String,
		val gender: Gender,
		val profileImageCode: String,
		val age: Int,
	)

	companion object {
		/** 추천이 없으면(null) null을 그대로 돌려준다. (추천 없음 = data:null) */
		fun of(recommendedTeam: RecommendedTeam?, today: LocalDate): RecommendedTeamResponse? =
			recommendedTeam?.let {
				RecommendedTeamResponse(
					teamId = it.teamId,
					name = it.name,
					introduction = it.introduction,
					members = it.members.map { member: RecommendedTeamMember ->
						Member(
							userId = member.userId,
							nickname = member.nickname,
							gender = member.gender,
							profileImageCode = member.profileImageCode,
							age = member.birthday.ageAt(today),
						)
					},
				)
			}
	}
}
