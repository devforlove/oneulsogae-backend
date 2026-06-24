package com.org.meeple.api.match

import com.org.meeple.api.match.response.TeamMatchResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.match.command.application.port.`in`.SendTeamInterestUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 2:2(팀) 매칭의 팀 매칭(소개) 엔드포인트. (인증 필요)
 * - POST /{teamMatchId}/interest: 참가 팀의 ACTIVE 구성원이 팀을 대표해 관심을 보낸다.
 *   상대 팀이 아직 신청 안 했으면 신청(PARTIALLY_ACCEPTED), 이미 신청했으면 수락이 되어 성사(MATCHED)된다.
 */
@Tag(name = "팀 매칭(소개)", description = "결성된 두 팀의 매칭에 관심을 보내고 성사시키는 엔드포인트.")
@RestController
@RequestMapping("/team-matches/v1")
class TeamMatchController(
	private val sendTeamInterestUseCase: SendTeamInterestUseCase,
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
}
