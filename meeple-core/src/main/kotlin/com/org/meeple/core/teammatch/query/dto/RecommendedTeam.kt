package com.org.meeple.core.teammatch.query.dto

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDate
import java.time.LocalDateTime

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
	/** 팀 ACTIVE 구성원 중 가장 최근 로그인 시각(match_user 원천). ACTIVE 구성원이 없으면 null. */
	val lastLoginAt: LocalDateTime?,
	/** 이 팀에 관심을 보낼 때 드는 신청 비용. 매칭된 상대 팀이면 team_matches.date_init_amount(DB), 순수 추천이면 MEETING_INIT 상수. */
	val datingInitAmount: Int,
	/** 이 팀에 관심을 보낼 때 드는 수락 비용. 매칭된 상대 팀이면 team_matches.date_accept_amount(DB), 순수 추천이면 MEETING_ACCEPT 상수. */
	val datingAcceptAmount: Int,
	/** 이 팀과의 팀 매칭(team_matches) id. 관심 보내기 호출에 쓴다. 아직 매칭이 없는 순수 추천이면 null. */
	val teamMatchId: Long?,
	/** 이 팀과의 팀 매칭 상태. 매칭된 상대 팀이면 team_matches.status, 아직 매칭이 없는 순수 추천이면 null. */
	val teamMatchStatus: MatchStatus?,
	/** 이 팀과의 팀 매칭 만료 시각. 매칭된 상대 팀이면 team_matches.expires_at, 아직 매칭이 없는 순수 추천이면 null. */
	val teamMatchExpiresAt: LocalDateTime?,
	/** 내 팀이 이 매칭에 관심(신청)을 보냈는지 여부. 매칭이 없으면 false. */
	val hasUserInterest: Boolean,
	/** 상대 팀이 이 매칭에 관심(신청)을 보냈는지 여부. 매칭이 없으면 false. */
	val hasPartnerInterest: Boolean,
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
	val universityName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val birthday: LocalDate,
	val height: Int?,
	val activityArea: String?,
	val introduction: String?,
	val traits: List<String>,
	val interests: List<String>,
)
