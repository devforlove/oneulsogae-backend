package com.org.meeple.api.match

import com.org.meeple.api.match.request.InviteTeamRequest
import com.org.meeple.api.match.request.SearchInvitableUsersRequest
import com.org.meeple.api.match.request.UpdateTeamRequest
import com.org.meeple.api.match.response.InvitableUserResponse
import com.org.meeple.api.match.response.ReceivedInvitationResponse
import com.org.meeple.api.match.response.SentInvitationResponse
import com.org.meeple.api.match.response.TeamResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.teammatch.command.application.port.`in`.AcceptTeamInvitationUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.DisbandTeamUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.InviteTeamUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.UpdateTeamUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.WithdrawTeamInvitationUseCase
import com.org.meeple.core.teammatch.query.service.port.`in`.GetReceivedInvitationsUseCase
import com.org.meeple.core.teammatch.query.service.port.`in`.GetSentInvitationUseCase
import com.org.meeple.core.teammatch.query.service.port.`in`.SearchInvitableUsersUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RestController

/**
 * 2:2(팀) 매칭의 팀 엔드포인트. (모두 인증 필요)
 * - POST /invitation: 다른 사용자를 초대해 팀을 결성한다. 초대 대상은 초대중(INVITED) 구성원으로 담기고 팀은 초대중(INVITING) 상태로 만들어진다.
 * - PUT /{teamId}: 진행 중(INVITING)이거나 결성(ACTIVE)된 팀의 구성원이 팀 이름·소개·활동지역을 수정한다.
 * - POST /{teamId}/acceptance: 초대받은 사용자가 팀 초대를 수락한다. 전원 수락 시 팀이 결성(ACTIVE)된다.
 * - DELETE /{teamId}/invitation: 초대 단계(INVITING) 팀의 초대를 철회한다. (초대받은 사람의 거절 / 초대자의 취소)
 * - DELETE /{teamId}: 결성(ACTIVE)/해체중(DISBANDED) 팀에서 구성원이 떠난다. (남은 팀원이 있으면 DISBANDED, 마지막이면 DEACTIVATED·매칭 종료)
 */
@Tag(name = "팀 매칭", description = "2:2(팀) 매칭의 팀 엔드포인트. 팀 초대·수락·철회·해체 및 초대 가능 유저 검색을 제공한다.")
// [미팅 기능 미노출] 미팅(2:2 팀 매칭) 기능은 구현은 완료됐지만 출시 시점에는 노출하지 않는다.
// @RestController를 주석 처리해 빈 등록을 막아 /teams/v1 전체 엔드포인트가 열리지 않게 한다. (호출 시 404)
// 기능을 노출할 때 아래 @RestController 주석을 해제하고, 함께 비활성화한 지점(TeamMatchController·
// TeamMatchBatchScheduler·RecommendedTeamBatchScheduler·VerifyCompanyEmailService의 팀 추천 호출)과
// @Ignored 처리한 팀 E2E 테스트를 같이 복구한다.
// @RestController
@RequestMapping("/teams/v1")
class TeamController(
	private val inviteTeamUseCase: InviteTeamUseCase,
	private val updateTeamUseCase: UpdateTeamUseCase,
	private val acceptTeamInvitationUseCase: AcceptTeamInvitationUseCase,
	private val withdrawTeamInvitationUseCase: WithdrawTeamInvitationUseCase,
	private val disbandTeamUseCase: DisbandTeamUseCase,
	private val searchInvitableUsersUseCase: SearchInvitableUsersUseCase,
	private val getSentInvitationUseCase: GetSentInvitationUseCase,
	private val getReceivedInvitationsUseCase: GetReceivedInvitationsUseCase,
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

	/**
	 * 팀의 이름·소개·활동지역을 수정한다.
	 * 진행 중(INVITING)이거나 결성(ACTIVE)된 팀의 구성원이 요청 본문으로 받은 이름·소개·활동지역으로 전체 교체한다.
	 */
	@Operation(summary = "팀 정보 수정", description = "진행 중(INVITING)이거나 결성(ACTIVE)된 팀의 구성원이 팀 이름·소개·활동지역을 수정한다.")
	@PutMapping("/{teamId}")
	fun update(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
		@RequestBody @Valid request: UpdateTeamRequest,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(updateTeamUseCase.update(user.id, teamId, request.toCommand())))

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

	/** 결성(ACTIVE)/해체중(DISBANDED) 팀에서 구성원이 떠난다. (남은 팀원이 있으면 DISBANDED, 마지막이면 DEACTIVATED·매칭 종료) */
	@Operation(summary = "팀 해체", description = "결성(ACTIVE)/해체중(DISBANDED) 팀에서 구성원이 떠난다. 남은 팀원이 있으면 팀은 해체중(DISBANDED)이 되고 매칭은 유지되며 본인만 채팅방을 나간다. 마지막 구성원이 떠나면 팀이 비활성화(DEACTIVATED)되고 매칭에서도 빠진다.")
	@DeleteMapping("/{teamId}")
	fun disband(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(disbandTeamUseCase.disband(user.id, teamId)))

	/**
	 * 우리팀을 조회한다. 요청자가 ACTIVE 구성원인 가장 최근 팀(진행 중 초대(INVITING) 또는 결성(ACTIVE))을 반환한다.
	 * 속한 팀이 없으면 data=null(200). (요청자가 ACTIVE 구성원인 팀만 조회되므로 본인에게만 노출된다)
	 */
	@Operation(summary = "우리팀 조회", description = "요청자가 ACTIVE 구성원인 가장 최근 팀(진행 중 초대(INVITING) 또는 결성(ACTIVE))을 반환한다. 속한 팀이 없으면 data=null(200)을 반환한다.")
	@GetMapping("/invitation")
	fun getMyTeam(
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
}
