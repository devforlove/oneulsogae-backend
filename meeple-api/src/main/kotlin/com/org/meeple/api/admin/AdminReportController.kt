package com.org.meeple.api.admin

import com.org.meeple.api.admin.response.AdminReportPageResponse
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.report.query.service.port.`in`.GetAdminReportsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 신고 조회 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 유저 신고(to_user_id 존재)만 다룬다.
 */
@Tag(name = "어드민 신고", description = "어드민 백오피스 신고 조회. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/reports")
class AdminReportController(
	private val getAdminReportsUseCase: GetAdminReportsUseCase,
) {

	@Operation(summary = "신고 목록 조회", description = "유저 신고를 최신순으로 page(0부터)·size 페이징해 조회한다.")
	@GetMapping
	fun reports(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ApiResponse<AdminReportPageResponse> =
		ApiResponse.success(AdminReportPageResponse.of(getAdminReportsUseCase.getReports(page, size)))
}
