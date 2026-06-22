package com.org.meeple.core.match.query.dto

/**
 * 미팅탭 화면 집계(read model). 세 가지를 독립적으로 조회해 한 화면에 모은다.
 * - [recommendedTeam]: 팀 없는 솔로 유저에게 추천된 결성(ACTIVE) 팀. 추천이 없으면 null.
 * - [receivedInvitationCount]: 내가 INVITED 구성원인 INVITING 팀 개수.
 * - [myActiveTeam]: 내 가장 최근 결성(ACTIVE) 팀. 없으면 null.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class MeetingTab(
	val recommendedTeam: RecommendedTeam?,
	val receivedInvitationCount: Long,
	val myActiveTeam: MyActiveTeam?,
)

/**
 * 내 가장 최근 결성(ACTIVE) 팀의 표시 데이터. 내 프로필 이미지와 같은 팀 친구(상대 ACTIVE 구성원)의 프로필 이미지를 담는다.
 * (profileImageCode는 match_user에서 온다)
 */
data class MyActiveTeam(
	val teamId: Long,
	val myProfileImageCode: String,
	val partnerProfileImageCode: String,
)
