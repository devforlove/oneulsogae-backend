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
	val activityArea: String?,
	val members: List<Member>,
) {

	/**
	 * 추천 팀 구성원 항목. 닉네임·직업·회사명·성별·프로필이미지·나이에 더해
	 * 카드 상세 시트용 키·지역·자기소개·특성·관심사를 담는다. (비공개면 null/빈 배열)
	 */
	data class Member(
		val userId: Long,
		val nickname: String,
		val job: String?,
		val companyName: String?,
		val gender: Gender,
		val profileImageCode: String,
		val age: Int,
		val height: Int?,
		val activityArea: String?,
		val introduction: String?,
		val traits: List<String>,
		val interests: List<String>,
	)

	companion object {
		/** 추천 팀 목록을 응답 목록으로 변환한다. (추천 없음 = 빈 리스트) */
		fun of(recommendedTeams: List<RecommendedTeam>, today: LocalDate): List<RecommendedTeamResponse> =
			recommendedTeams.map { recommendedTeam: RecommendedTeam ->
				RecommendedTeamResponse(
					teamId = recommendedTeam.teamId,
					name = recommendedTeam.name,
					introduction = recommendedTeam.introduction,
					activityArea = recommendedTeam.activityArea,
					members = recommendedTeam.members.map { member: RecommendedTeamMember ->
						Member(
							userId = member.userId,
							nickname = member.nickname,
							job = member.job,
							companyName = member.companyName,
							gender = member.gender,
							profileImageCode = member.profileImageCode,
							age = member.birthday.ageAt(today),
							height = member.height,
							activityArea = member.activityArea,
							introduction = member.introduction,
							traits = member.traits,
							interests = member.interests,
						)
					},
				)
			}
	}
}
