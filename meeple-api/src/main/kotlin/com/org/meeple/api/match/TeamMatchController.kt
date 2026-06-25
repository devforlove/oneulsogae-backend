package com.org.meeple.api.match

import com.org.meeple.api.match.response.MeetingTabResponse
import com.org.meeple.api.match.response.TeamMatchResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.SendTeamInterestUseCase
import com.org.meeple.core.match.query.service.port.`in`.GetMeetingTabUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 2:2(팀) 매칭의 팀 매칭(소개) 엔드포인트. (인증 필요)
 * - POST /{teamMatchId}/interest: 참가 팀의 ACTIVE 구성원이 팀을 대표해 관심을 보낸다.
 *   상대 팀이 아직 신청 안 했으면 신청(PARTIALLY_ACCEPTED), 이미 신청했으면 수락이 되어 성사(MATCHED)된다.
 * - GET  /meeting-tab: 미팅탭 화면 데이터를 한 번에 조회한다.
 */
@Tag(name = "팀 매칭(소개)", description = "결성된 두 팀의 매칭에 관심을 보내고 성사시키는 엔드포인트. 미팅탭 화면 집계도 제공한다.")
@RestController
@RequestMapping("/team-matches/v1")
class TeamMatchController(
	private val sendTeamInterestUseCase: SendTeamInterestUseCase,
	private val getMeetingTabUseCase: GetMeetingTabUseCase,
	private val timeGenerator: TimeGenerator,
) {

	@Operation(
		summary = "팀 관심 보내기",
		description = "참가 팀의 ACTIVE 구성원이 팀을 대표해 관심을 보낸다. 상대 팀이 이미 신청했으면 성사(MATCHED)되어 4인 채팅방이 생성된다. 신청/수락 비용은 행위한 구성원이 부담한다.",
	)
	@PostMapping("/{teamMatchId}/interest")
	fun sendInterest(
		@LoginUser user: AuthUser,
		@PathVariable teamMatchId: Long,
	): ApiResponse<TeamMatchResponse> =
		ApiResponse.success(TeamMatchResponse.of(sendTeamInterestUseCase.sendInterest(user.id, teamMatchId)))

	/**
	 * 미팅탭 화면 데이터를 한 번에 조회한다.
	 * - recommendedTeams: 팀 카드 목록(최신순, 없으면 빈 리스트). 결성(ACTIVE) 팀이 없으면 추천된 팀(반대 성별·같은 권역), 결성 팀이 있으면 그 팀과 진행 중으로 매칭된 상대 팀.
	 * - receivedInvitationCount: 내가 INVITED인 INVITING 팀 개수.
	 * - myTeam: 내 가장 최근 팀(결성(ACTIVE) 또는 내가 만든 초대중(INVITING))의 teamId와 내/상대 profileImageCode. 없으면 null.
	 */
	@Operation(summary = "미팅탭 조회", description = "미팅탭 화면 데이터를 한 번에 반환한다. 팀 카드 목록(결성 팀 없으면 추천 팀, 있으면 매칭된 상대 팀, 없으면 빈 리스트), 받은 초대(INVITED) 개수, 내 결성(ACTIVE) 팀의 teamId·내/친구 프로필 이미지(없으면 null)를 담는다.")
	@GetMapping("/meeting-tab")
	fun getMeetingTab(
		@LoginUser user: AuthUser,
	): ApiResponse<MeetingTabResponse> =
		ApiResponse.success(MeetingTabResponse.of(getMeetingTabUseCase.get(user.id), timeGenerator.today()))
}
