package com.org.meeple.api.match

import com.org.meeple.api.match.request.InviteTeamRequest
import com.org.meeple.api.match.request.SearchInvitableUsersRequest
import com.org.meeple.api.match.response.InvitableUserResponse
import com.org.meeple.api.match.response.MeetingTabResponse
import com.org.meeple.api.match.response.ReceivedInvitationResponse
import com.org.meeple.api.match.response.SentInvitationResponse
import com.org.meeple.api.match.response.TeamResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.AcceptTeamInvitationUseCase
import com.org.meeple.core.match.command.application.port.`in`.DisbandTeamUseCase
import com.org.meeple.core.match.command.application.port.`in`.InviteTeamUseCase
import com.org.meeple.core.match.command.application.port.`in`.WithdrawTeamInvitationUseCase
import com.org.meeple.core.match.query.service.port.`in`.GetMeetingTabUseCase
import com.org.meeple.core.match.query.service.port.`in`.GetReceivedInvitationsUseCase
import com.org.meeple.core.match.query.service.port.`in`.GetSentInvitationUseCase
import com.org.meeple.core.match.query.service.port.`in`.SearchInvitableUsersUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RestController

/**
 * 2:2(팀) 매칭의 팀 엔드포인트. (모두 인증 필요)
 * - POST /invitation: 다른 사용자를 초대해 팀을 결성한다. 초대 대상은 초대중(INVITED) 구성원으로 담기고 팀은 초대중(INVITING) 상태로 만들어진다.
 * - POST /{teamId}/acceptance: 초대받은 사용자가 팀 초대를 수락한다. 전원 수락 시 팀이 결성(ACTIVE)된다.
 * - DELETE /{teamId}/invitation: 초대 단계(INVITING) 팀의 초대를 철회한다. (초대받은 사람의 거절 / 초대자의 취소)
 * - DELETE /{teamId}: 결성(ACTIVE)된 팀을 구성원이 해체한다. (떠나면 2인 팀이 유지될 수 없어 팀 전체 비활성화)
 */
@Tag(name = "팀 매칭", description = "2:2(팀) 매칭의 팀 엔드포인트. 팀 초대·수락·철회·해체 및 초대 가능 유저 검색을 제공한다.")
@RestController
@RequestMapping("/teams/v1")
class TeamController(
	private val inviteTeamUseCase: InviteTeamUseCase,
	private val acceptTeamInvitationUseCase: AcceptTeamInvitationUseCase,
	private val withdrawTeamInvitationUseCase: WithdrawTeamInvitationUseCase,
	private val disbandTeamUseCase: DisbandTeamUseCase,
	private val searchInvitableUsersUseCase: SearchInvitableUsersUseCase,
	private val getSentInvitationUseCase: GetSentInvitationUseCase,
	private val getReceivedInvitationsUseCase: GetReceivedInvitationsUseCase,
	private val getMeetingTabUseCase: GetMeetingTabUseCase,
	private val timeGenerator: TimeGenerator,
) {

	/**
	 * 다른 사용자를 초대해 팀을 결성한다.
	 * 요청 본문으로 팀 이름·소개·초대할 사용자 id를 받고, 초대자(인증 사용자)와 초대 대상을 구성원으로 담은 팀을 만든다.
	 */
	@Operation(summary = "팀 초대", description = "다른 사용자를 초대해 팀을 결성한다. 초대 대상은 초대중(INVITED) 구성원으로 담기고 팀은 초대중(INVITING) 상태로 만들어진다.")
	@PostMapping("/invitation")
	fun invite(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: InviteTeamRequest,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(inviteTeamUseCase.invite(user.id, request.toCommand())))

	/** 초대받은 사용자가 팀 초대를 수락한다. 전원 수락 시 팀이 결성(ACTIVE)된다. */
	@Operation(summary = "팀 초대 수락", description = "초대받은 사용자가 팀 초대를 수락한다. 전원 수락 시 팀이 결성(ACTIVE)된다.")
	@PostMapping("/{teamId}/acceptance")
	fun accept(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(acceptTeamInvitationUseCase.accept(user.id, teamId)))

	/** 초대 단계(INVITING) 팀의 초대를 철회한다. (초대받은 사람의 거절 / 초대자의 취소) */
	@Operation(summary = "팀 초대 철회", description = "초대 단계(INVITING) 팀의 초대를 철회한다. 초대받은 사람의 거절 또는 초대자의 취소 시 사용한다.")
	@DeleteMapping("/{teamId}/invitation")
	fun withdrawInvitation(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<Unit> {
		withdrawTeamInvitationUseCase.withdraw(user.id, teamId)
		return ApiResponse.success()
	}

	/** 결성(ACTIVE)된 팀을 구성원이 해체한다. (떠나면 2인 팀이 유지될 수 없어 팀 전체 비활성화) */
	@Operation(summary = "팀 해체", description = "결성(ACTIVE)된 팀을 구성원이 해체한다. 구성원이 떠나면 2인 팀이 유지될 수 없어 팀 전체가 비활성화된다.")
	@DeleteMapping("/{teamId}")
	fun disband(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(disbandTeamUseCase.disband(user.id, teamId)))

	/**
	 * 내가 보낸 초대 현황을 조회한다. 요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 반환한다.
	 * 진행 중인 초대가 없으면 data=null(200). (초대받은 사람·비구성원은 조회되지 않아 초대자에게만 노출된다)
	 */
	@Operation(summary = "내가 보낸 초대 현황 조회", description = "요청자가 초대자(ACTIVE 구성원)인 가장 최근 INVITING 팀을 반환한다. 진행 중인 초대가 없으면 data=null(200)을 반환한다.")
	@GetMapping("/invitation")
	fun getSentInvitation(
		@LoginUser user: AuthUser,
	): ApiResponse<SentInvitationResponse?> =
		ApiResponse.success(SentInvitationResponse.of(getSentInvitationUseCase.get(user.id), timeGenerator.today()))

	/**
	 * 내가 받은(INVITED) 대기 중 초대 리스트를 최신순으로 조회한다. 각 항목은 팀 메타와 초대자(owner) 프로필을 담는다.
	 */
	@Operation(summary = "받은 초대 리스트 조회", description = "요청자가 INVITED 상태인 INVITING 팀들을 최신순으로 반환한다. 각 항목에 팀 메타, 내가 초대된 시각(invitedAt), 그 팀의 ACTIVE 구성원 목록(participants, 성별·키·지역·자기소개·특성·관심사 포함)을 담는다.")
	@GetMapping("/received-invitations")
	fun getReceivedInvitations(
		@LoginUser user: AuthUser,
	): ApiResponse<List<ReceivedInvitationResponse>> =
		ApiResponse.success(ReceivedInvitationResponse.listOf(getReceivedInvitationsUseCase.get(user.id), timeGenerator.today()))

	/**
	 * 닉네임이 정확히 일치하는 초대 가능 유저를 검색한다. (같은 성별·매칭 가능·활성 팀 없음·자기 제외)
	 * 결과 항목은 식별자·닉네임·직업·회사명을 담는다.
	 */
	@Operation(summary = "초대 가능 유저 검색", description = "닉네임이 정확히 일치하는 초대 가능 유저를 검색한다. 같은 성별·매칭 가능 상태·활성 팀 없음·자기 자신 제외 조건을 적용하며, 결과에 식별자·닉네임·직업·회사명을 포함한다.")
	@GetMapping("/invitable-users")
	fun searchInvitableUsers(
		@LoginUser user: AuthUser,
		@ModelAttribute @Valid request: SearchInvitableUsersRequest,
	): ApiResponse<List<InvitableUserResponse>> =
		ApiResponse.success(InvitableUserResponse.listOf(searchInvitableUsersUseCase.search(user.id, request.nickname!!), timeGenerator.today()))

	/**
	 * 미팅탭 화면 데이터를 한 번에 조회한다.
	 * - recommendedTeam: 팀 없는 솔로 유저에게 추천된 결성(ACTIVE) 팀(반대 성별·같은 권역). 추천이 없으면 null.
	 * - receivedInvitationCount: 내가 INVITED인 INVITING 팀 개수.
	 * - myActiveTeam: 내 가장 최근 결성(ACTIVE) 팀의 teamId와 내/친구 profileImageCode. 없으면 null.
	 */
	@Operation(summary = "미팅탭 조회", description = "미팅탭 화면 데이터를 한 번에 반환한다. 추천 팀(없으면 null), 받은 초대(INVITED) 개수, 내 결성(ACTIVE) 팀의 teamId·내/친구 프로필 이미지(없으면 null)를 담는다.")
	@GetMapping("/meeting-tab")
	fun getMeetingTab(
		@LoginUser user: AuthUser,
	): ApiResponse<MeetingTabResponse> =
		ApiResponse.success(MeetingTabResponse.of(getMeetingTabUseCase.get(user.id), timeGenerator.today()))
}
