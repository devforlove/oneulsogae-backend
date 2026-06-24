package com.org.meeple.core.match.query.dto

import com.org.meeple.common.user.Gender
import java.time.LocalDate

/**
 * 솔로 유저에게 추천된 팀 한 건(read model). 팀 메타와 그 팀의 ACTIVE 구성원 프로필을 담는다.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class RecommendedTeam(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	/** 팀 활동지역명 "시/도 시/군/구"(teams.region_id ⋈ regions). 지역 미설정이면 null. */
	val activityArea: String?,
	val members: List<RecommendedTeamMember>,
)

/**
 * 추천 팀 구성원 한 명의 표시 프로필. (받은 초대 카드와 동일한 구성원 프로필 필드셋)
 * 닉네임·성별·프로필이미지·생일은 match_user, 직업·회사명·키·지역·자기소개·특성·관심사는 user_details에서 온다.
 * 키·지역·자기소개·특성·관심사는 카드 상세 시트에서 쓰는 값으로, 비공개면 null/빈 배열일 수 있다.
 */
data class RecommendedTeamMember(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val birthday: LocalDate,
	val height: Int?,
	val activityArea: String?,
	val introduction: String?,
	val traits: List<String>,
	val interests: List<String>,
)
