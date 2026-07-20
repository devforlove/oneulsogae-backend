package com.org.oneulsogae.api.report

import com.org.oneulsogae.api.report.request.CreateReportRequest
import com.org.oneulsogae.api.report.request.CreateTargetReportRequest
import com.org.oneulsogae.api.report.response.ReportResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.report.command.application.port.`in`.CreateReportUseCase
import com.org.oneulsogae.core.report.command.application.port.`in`.CreateTargetReportUseCase
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
	private val createTargetReportUseCase: CreateTargetReportUseCase,
) {

	@Operation(summary = "신고 생성", description = "신고자가 채팅방(chatRoomId)에서 사유와 함께 신고한다. 신고 대상(상대 유저/팀)은 채팅방의 매칭 정보로 서버가 정한다.")
	@PostMapping
	fun create(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CreateReportRequest,
	): ApiResponse<ReportResponse> =
		ApiResponse.success(ReportResponse.of(createReportUseCase.create(user.id, request.toCommand())))

	@Operation(summary = "대상 직접 지정 신고", description = "채팅방 없이 신고 대상 종류(targetType: USER/TEAM)와 대상 id(targetId)로 사유와 함께 신고한다. 소개(1:1)는 USER, 미팅(팀)은 TEAM.")
	@PostMapping("/targets")
	fun createForTarget(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CreateTargetReportRequest,
	): ApiResponse<ReportResponse> =
		ApiResponse.success(ReportResponse.of(createTargetReportUseCase.create(user.id, request.toCommand())))
}
