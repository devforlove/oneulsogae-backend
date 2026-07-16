package com.org.meeple.api.admin

import com.org.meeple.admin.gathering.command.application.port.`in`.ApproveGatheringMemberUseCase
import com.org.meeple.admin.gathering.command.application.port.`in`.RejectGatheringMemberUseCase
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberView
import com.org.meeple.admin.gathering.query.service.port.`in`.GetAdminGatheringMembersUseCase
import com.org.meeple.api.admin.response.AdminGatheringMemberResponse
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 모임 참가 신청 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 결제완료로 승인대기(PENDING)가 된 신청을 입금 확인 후 승인(JOINED)하거나 거절(REJECTED)한다.
 * - GET /: 일정별 참가 신청 목록(닉네임·성별·상태·결제금액·신청 시각, 신청 순).
 * - POST /{memberId}/approve: 승인. 없으면 404(GATHER-019), 승인대기 아님 409(GATHER-020).
 * - POST /{memberId}/reject: 거절 + 일정 여분(성별·얼리버드) 복원. 에러는 승인과 동일.
 */
@Tag(name = "어드민 모임 참가 신청", description = "어드민 백오피스 참가 신청 목록 조회·승인·거절. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/gatherings/schedules/{scheduleId}/members")
class AdminGatheringMemberController(
	private val approveGatheringMemberUseCase: ApproveGatheringMemberUseCase,
	private val rejectGatheringMemberUseCase: RejectGatheringMemberUseCase,
	private val getAdminGatheringMembersUseCase: GetAdminGatheringMembersUseCase,
) {

	@Operation(
		summary = "참가 신청 목록 조회",
		description = "일정의 참가 신청을 신청 순으로 조회한다. 닉네임·성별·상태·결제금액(최신 결제 기록)·신청 시각을 담는다.",
	)
	@GetMapping
	fun list(@PathVariable scheduleId: Long): ApiResponse<List<AdminGatheringMemberResponse>> =
		ApiResponse.success(
			getAdminGatheringMembersUseCase.getByScheduleId(scheduleId).values
				.map { view: AdminGatheringMemberView -> AdminGatheringMemberResponse.of(view) },
		)

	@Operation(
		summary = "참가 신청 승인",
		description = "승인대기(PENDING) 신청을 참가(JOINED)로 전이한다. 여분은 접수 시 이미 차감되어 바뀌지 않는다. " +
			"신청이 없거나 해당 일정 소속이 아니면 404(GATHER-019), 승인대기가 아니면 409(GATHER-020).",
	)
	@PostMapping("/{memberId}/approve")
	fun approve(
		@PathVariable scheduleId: Long,
		@PathVariable memberId: Long,
	): ApiResponse<Unit> {
		approveGatheringMemberUseCase.approve(scheduleId, memberId)
		return ApiResponse.success()
	}

	@Operation(
		summary = "참가 신청 거절",
		description = "승인대기(PENDING) 신청을 거절(REJECTED)로 전이하고 접수 시 차감한 일정 여분(성별·얼리버드)을 복원한다. " +
			"신청이 없거나 해당 일정 소속이 아니면 404(GATHER-019), 승인대기가 아니면 409(GATHER-020).",
	)
	@PostMapping("/{memberId}/reject")
	fun reject(
		@PathVariable scheduleId: Long,
		@PathVariable memberId: Long,
	): ApiResponse<Unit> {
		rejectGatheringMemberUseCase.reject(scheduleId, memberId)
		return ApiResponse.success()
	}
}
