package com.org.oneulsogae.api.match.response

import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.teammatch.query.dto.MeetingTab
import com.org.oneulsogae.core.teammatch.query.dto.MyTeam
import java.time.LocalDate

/**
 * 미팅탭 화면 응답. 팀 카드 목록·받은 초대 개수·내 팀(결성 또는 초대중)을 한 번에 담는다.
 * - [recommendedTeams]: 결성 팀이 없으면 추천 팀, 있으면 내 팀과 진행 중으로 매칭된 상대 팀. (없으면 빈 리스트)
 * - [receivedInvitationCount]: 내가 INVITED인 INVITING 팀 개수.
 * - [myTeam]: 내 팀(결성(ACTIVE) 또는 내가 만든 초대중(INVITING))이 없으면 null.
 */
data class MeetingTabResponse(
	val recommendedTeams: List<RecommendedTeamResponse>,
	val receivedInvitationCount: Long,
	val myTeam: MyTeamResponse?,
) {

	/** 내 팀(결성(ACTIVE)·초대중(INVITING)·해체중(DISBANDED))의 표시 데이터. 팀 상태·성별과 내/상대(친구 또는 초대 대상) 프로필 이미지 코드. */
	data class MyTeamResponse(
		val teamId: Long,
		/** 팀 상태(백엔드 TeamStatus enum name: ACTIVE=결성됨 / INVITING=초대중 / DISBANDED=해체중). */
		val status: TeamStatus,
		val gender: Gender,
		val myProfileImageCode: String,
		/** 같은 팀 상대의 프로필 이미지 코드. 해체중(DISBANDED)이라 상대가 이미 나갔으면 null. */
		val partnerProfileImageCode: String?,
	)

	companion object {
		fun of(meetingTab: MeetingTab, today: LocalDate): MeetingTabResponse =
			MeetingTabResponse(
				recommendedTeams = RecommendedTeamResponse.of(meetingTab.recommendedTeams, today),
				receivedInvitationCount = meetingTab.receivedInvitationCount,
				myTeam = meetingTab.myTeam?.let { team: MyTeam ->
					MyTeamResponse(
						teamId = team.teamId,
						status = team.status,
						gender = team.gender,
						myProfileImageCode = team.myProfileImageCode,
						partnerProfileImageCode = team.partnerProfileImageCode,
					)
				},
			)
	}
}
