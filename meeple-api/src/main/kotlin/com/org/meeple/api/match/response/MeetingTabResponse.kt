package com.org.meeple.api.match.response

import com.org.meeple.core.match.query.dto.MeetingTab
import com.org.meeple.core.match.query.dto.MyActiveTeam
import java.time.LocalDate

/**
 * 미팅탭 화면 응답. 추천 팀·받은 초대 개수·내 결성(ACTIVE) 팀을 한 번에 담는다.
 * - [recommendedTeam]: 추천이 없으면 null.
 * - [receivedInvitationCount]: 내가 INVITED인 INVITING 팀 개수.
 * - [myActiveTeam]: 내 결성 팀이 없으면 null.
 */
data class MeetingTabResponse(
	val recommendedTeam: RecommendedTeamResponse?,
	val receivedInvitationCount: Long,
	val myActiveTeam: MyActiveTeamResponse?,
) {

	/** 내 결성(ACTIVE) 팀의 표시 데이터. 내/친구 프로필 이미지 코드. */
	data class MyActiveTeamResponse(
		val teamId: Long,
		val myProfileImageCode: String,
		val partnerProfileImageCode: String,
	)

	companion object {
		fun of(meetingTab: MeetingTab, today: LocalDate): MeetingTabResponse =
			MeetingTabResponse(
				recommendedTeam = RecommendedTeamResponse.of(meetingTab.recommendedTeam, today),
				receivedInvitationCount = meetingTab.receivedInvitationCount,
				myActiveTeam = meetingTab.myActiveTeam?.let { team: MyActiveTeam ->
					MyActiveTeamResponse(
						teamId = team.teamId,
						myProfileImageCode = team.myProfileImageCode,
						partnerProfileImageCode = team.partnerProfileImageCode,
					)
				},
			)
	}
}
