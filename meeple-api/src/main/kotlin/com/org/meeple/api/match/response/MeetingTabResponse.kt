package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.MeetingTab
import com.org.meeple.core.match.query.dto.MyActiveTeam
import java.time.LocalDate

/**
 * 미팅탭 화면 응답. 팀 카드 목록·받은 초대 개수·내 결성(ACTIVE) 팀을 한 번에 담는다.
 * - [recommendedTeams]: 결성 팀이 없으면 추천 팀, 있으면 내 팀과 진행 중으로 매칭된 상대 팀. (없으면 빈 리스트)
 * - [receivedInvitationCount]: 내가 INVITED인 INVITING 팀 개수.
 * - [myActiveTeam]: 내 결성 팀이 없으면 null.
 */
data class MeetingTabResponse(
	val recommendedTeams: List<RecommendedTeamResponse>,
	val receivedInvitationCount: Long,
	val myActiveTeam: MyActiveTeamResponse?,
) {

	/** 내 결성(ACTIVE) 팀의 표시 데이터. 팀 성별과 내/친구 프로필 이미지 코드. */
	data class MyActiveTeamResponse(
		val teamId: Long,
		val gender: Gender,
		val myProfileImageCode: String,
		val partnerProfileImageCode: String,
	)

	companion object {
		fun of(meetingTab: MeetingTab, today: LocalDate): MeetingTabResponse =
			MeetingTabResponse(
				recommendedTeams = RecommendedTeamResponse.of(meetingTab.recommendedTeams, today),
				receivedInvitationCount = meetingTab.receivedInvitationCount,
				myActiveTeam = meetingTab.myActiveTeam?.let { team: MyActiveTeam ->
					MyActiveTeamResponse(
						teamId = team.teamId,
						gender = team.gender,
						myProfileImageCode = team.myProfileImageCode,
						partnerProfileImageCode = team.partnerProfileImageCode,
					)
				},
			)
	}
}
