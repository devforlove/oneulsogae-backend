package com.org.meeple.api.match

import com.org.meeple.api.match.request.InviteTeamRequest
import com.org.meeple.api.match.response.TeamResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.match.command.application.port.`in`.AcceptTeamInvitationUseCase
import com.org.meeple.core.match.command.application.port.`in`.DisbandTeamUseCase
import com.org.meeple.core.match.command.application.port.`in`.InviteTeamUseCase
import com.org.meeple.core.match.command.application.port.`in`.WithdrawTeamInvitationUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 2:2(팀) 매칭의 팀 엔드포인트. (모두 인증 필요)
 * - POST /: 다른 사용자를 초대해 팀을 결성한다. 초대 대상은 초대중(INVITED) 구성원으로 담기고 팀은 초대중(INVITING) 상태로 만들어진다.
 * - POST /{teamId}/acceptance: 초대받은 사용자가 팀 초대를 수락한다. 전원 수락 시 팀이 결성(FORMED)된다.
 * - DELETE /{teamId}/invitation: 초대 단계(INVITING) 팀의 초대를 철회한다. (초대받은 사람의 거절 / 초대자의 취소)
 * - DELETE /{teamId}: 결성(FORMED)된 팀을 구성원이 해체한다. (떠나면 2인 팀이 유지될 수 없어 팀 전체 비활성화)
 */
@RestController
@RequestMapping("/teams/v1")
class TeamController(
	private val inviteTeamUseCase: InviteTeamUseCase,
	private val acceptTeamInvitationUseCase: AcceptTeamInvitationUseCase,
	private val withdrawTeamInvitationUseCase: WithdrawTeamInvitationUseCase,
	private val disbandTeamUseCase: DisbandTeamUseCase,
) {

	/**
	 * 다른 사용자를 초대해 팀을 결성한다.
	 * 요청 본문으로 팀 이름·소개·초대할 사용자 id를 받고, 초대자(인증 사용자)와 초대 대상을 구성원으로 담은 팀을 만든다.
	 */
	@PostMapping
	fun invite(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: InviteTeamRequest,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(inviteTeamUseCase.invite(user.id, request.toCommand())))

	/** 초대받은 사용자가 팀 초대를 수락한다. 전원 수락 시 팀이 결성(FORMED)된다. */
	@PostMapping("/{teamId}/acceptance")
	fun accept(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(acceptTeamInvitationUseCase.accept(user.id, teamId)))

	/** 초대 단계(INVITING) 팀의 초대를 철회한다. (초대받은 사람의 거절 / 초대자의 취소) */
	@DeleteMapping("/{teamId}/invitation")
	fun withdrawInvitation(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(withdrawTeamInvitationUseCase.withdraw(user.id, teamId)))

	/** 결성(FORMED)된 팀을 구성원이 해체한다. (떠나면 2인 팀이 유지될 수 없어 팀 전체 비활성화) */
	@DeleteMapping("/{teamId}")
	fun disband(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(disbandTeamUseCase.disband(user.id, teamId)))
}
