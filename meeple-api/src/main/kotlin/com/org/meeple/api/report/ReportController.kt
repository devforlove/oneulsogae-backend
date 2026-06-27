package com.org.meeple.api.report

import com.org.meeple.api.report.request.CreateReportRequest
import com.org.meeple.api.report.response.ReportResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.report.command.application.port.`in`.CreateReportUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "신고", description = "사용자 신고 API")
@RestController
@RequestMapping("/reports/v1")
class ReportController(
	private val createReportUseCase: CreateReportUseCase,
) {

	@Operation(summary = "신고 생성", description = "신고자가 채팅방(chatRoomId)에서 사유와 함께 신고한다. 신고 대상(상대 유저/팀)은 채팅방의 매칭 정보로 서버가 정한다.")
	@PostMapping
	fun create(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CreateReportRequest,
	): ApiResponse<ReportResponse> =
		ApiResponse.success(ReportResponse.of(createReportUseCase.create(user.id, request.toCommand())))
}
