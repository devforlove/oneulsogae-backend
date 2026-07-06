package com.org.meeple.api.admin

import com.org.meeple.admin.gathering.command.application.port.`in`.ChangeGatheringScheduleStatusUseCase
import com.org.meeple.admin.gathering.command.application.port.`in`.CreateGatheringScheduleUseCase
import com.org.meeple.api.admin.request.ChangeGatheringScheduleStatusRequest
import com.org.meeple.api.admin.request.CreateGatheringScheduleRequest
import com.org.meeple.api.admin.response.CreateGatheringScheduleResponse
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 모임 일정 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 한 모임(gatheringId) 하위에 여러 일정을 둘 수 있다(1:N).
 * - POST /: 시작·종료 시각으로 일정 생성(예정 상태로 시작). 모임이 없으면 404(GATHER-008), 시간 규칙 위반 400(GATHER-015·016).
 * - POST /{scheduleId}/status: 일정 상태 전이. 없으면 404(GATHER-014), 전이 불가면 409(GATHER-017).
 */
@Tag(name = "어드민 모임 일정", description = "어드민 백오피스 모임 일정 등록·상태 변경. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/gatherings/{gatheringId}/schedules")
class AdminGatheringScheduleController(
	private val createGatheringScheduleUseCase: CreateGatheringScheduleUseCase,
	private val changeGatheringScheduleStatusUseCase: ChangeGatheringScheduleStatusUseCase,
) {

	@Operation(
		summary = "모임 일정 생성",
		description = "모임에 일정을 추가한다. startAt(필수)·endAt(선택)로 시간 범위를 지정하며 예정(SCHEDULED) 상태로 생성된다. " +
			"모임이 없으면 404(GATHER-008), 시작이 현재 이전이면 400(GATHER-015), 종료가 시작 이전이면 400(GATHER-016).",
	)
	@PostMapping
	fun create(
		@PathVariable gatheringId: Long,
		@RequestBody @Valid request: CreateGatheringScheduleRequest,
	): ApiResponse<CreateGatheringScheduleResponse> =
		ApiResponse.success(
			CreateGatheringScheduleResponse.of(createGatheringScheduleUseCase.create(request.toCommand(gatheringId))),
		)

	@Operation(
		summary = "모임 일정 상태 변경",
		description = "일정 상태를 전이한다. 예정→진행중·취소, 진행중→종료·취소가 가능하다. " +
			"일정이 없거나 해당 모임 소속이 아니면 404(GATHER-014), 전이 불가면 409(GATHER-017).",
	)
	@PostMapping("/{scheduleId}/status")
	fun changeStatus(
		@PathVariable gatheringId: Long,
		@PathVariable scheduleId: Long,
		@RequestBody @Valid request: ChangeGatheringScheduleStatusRequest,
	): ApiResponse<Unit> {
		changeGatheringScheduleStatusUseCase.changeStatus(gatheringId, scheduleId, request.status!!)
		return ApiResponse.success()
	}
}
