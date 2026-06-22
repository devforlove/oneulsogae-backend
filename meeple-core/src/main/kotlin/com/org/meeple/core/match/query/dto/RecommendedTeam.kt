package com.org.meeple.core.match.query.dto

import com.org.meeple.common.user.Gender
import java.time.LocalDate

/**
 * 솔로 유저에게 추천된 팀 한 건(read model). 팀 메타와 그 팀의 ACTIVE 구성원 프로필을 담는다.
 * query 전용 view이며 command 도메인을 참조하지 않는다. (닉네임·프로필이미지·생일은 match_user, 성별은 team_members)
 */
data class RecommendedTeam(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val members: List<RecommendedTeamMember>,
)

/** 추천 팀 구성원 한 명의 표시 프로필. */
data class RecommendedTeamMember(
	val userId: Long,
	val nickname: String,
	val gender: Gender,
	val profileImageCode: String,
	val birthday: LocalDate,
)
