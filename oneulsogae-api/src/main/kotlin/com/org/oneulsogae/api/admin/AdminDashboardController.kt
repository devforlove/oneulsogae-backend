package com.org.oneulsogae.api.admin

import com.org.oneulsogae.api.admin.response.AdminDashboardResponse
import com.org.oneulsogae.admin.dashboard.query.service.port.`in`.GetAdminDashboardUseCase
import com.org.oneulsogae.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 대시보드 엔드포인트. `/admin` 하위 경로는 SecurityConfig의 hasRole(ADMIN) 규칙으로 보호된다.
 * - GET /dashboard: 전체 사용자·금일 가입자·금일 DAU·금일 코인 결제액·진행중 매치·미처리 신고를 한 번에 조회한다.
 */
@Tag(name = "어드민", description = "어드민 백오피스 엔드포인트. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1")
class AdminDashboardController(
	private val getAdminDashboardUseCase: GetAdminDashboardUseCase,
) {

	@Operation(
		summary = "어드민 대시보드 조회",
		description = "전체 사용자 수, 금일 가입자 수(계정 생성 기준), 금일 DAU(last_login_at 기준), 금일 코인 결제액(PURCHASE 적립 합), 진행중 매치 수(솔로·팀), 미처리 신고 수를 한 번에 반환한다.",
	)
	@GetMapping("/dashboard")
	fun getDashboard(): ApiResponse<AdminDashboardResponse> =
		ApiResponse.success(AdminDashboardResponse.of(getAdminDashboardUseCase.get()))
}
