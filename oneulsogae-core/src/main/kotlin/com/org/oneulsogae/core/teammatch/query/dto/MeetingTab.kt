package com.org.oneulsogae.core.teammatch.query.dto

import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender

/**
 * 미팅탭 화면 집계(read model). 세 가지를 독립적으로 조회해 한 화면에 모은다.
 * - [recommendedTeams]: 팀 카드 목록(최신순, 없으면 빈 리스트). 결성 팀이 없으면 추천된 결성(ACTIVE) 팀, 결성 팀이 있으면 그 팀과 진행 중으로 매칭된 상대 팀.
 * - [receivedInvitationCount]: 내가 INVITED 구성원인 INVITING 팀 개수.
 * - [myTeam]: 내 가장 최근 팀(결성(ACTIVE) 또는 내가 만든 초대중(INVITING)). 없으면 null.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class MeetingTab(
	val recommendedTeams: List<RecommendedTeam>,
	val receivedInvitationCount: Long,
	val myTeam: MyTeam?,
)

/**
 * 내 가장 최근 팀(결성(ACTIVE)·초대중(INVITING)·해체중(DISBANDED))의 표시 데이터. 내 프로필 이미지와 같은 팀 상대(친구 또는 초대 대상)의 프로필 이미지를 담는다.
 * (profileImageCode는 match_user에서 온다)
 */
data class MyTeam(
	val teamId: Long,
	/** 팀 상태. 결성됨(ACTIVE)·내가 만든 초대중(INVITING)·해체중(DISBANDED). */
	val status: TeamStatus,
	val gender: Gender,
	val myProfileImageCode: String,
	/** 같은 팀 상대(친구 또는 초대 대상)의 프로필 이미지. 해체중(DISBANDED)이라 상대가 이미 나갔으면 null. */
	val partnerProfileImageCode: String?,
)
