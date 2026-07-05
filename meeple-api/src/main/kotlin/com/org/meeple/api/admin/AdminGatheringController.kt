package com.org.meeple.api.admin

import com.org.meeple.admin.gathering.command.application.port.`in`.CreateAdminGatheringUseCase
import com.org.meeple.api.admin.request.CreateAdminGatheringRequest
import com.org.meeple.api.admin.response.CreateAdminGatheringResponse
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 모임 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * - POST /: 종류·제목·소개·지역·일시·정원·참가비(성별·티어)로 모임을 생성한다. (운영 생성 → user_id null)
 */
@Tag(name = "어드민 모임", description = "어드민 백오피스 모임 등록. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/gatherings")
class AdminGatheringController(
	private val createAdminGatheringUseCase: CreateAdminGatheringUseCase,
) {

	@Operation(
		summary = "모임 생성",
		description = "종류·제목·소개·지역·일시·정원·참가비(정상가 남/녀 필수, 얼리버드·할인가 남/녀 선택)로 모임을 등록한다. 운영이 만든 모임으로 저장된다.",
	)
	@PostMapping
	fun create(
		@RequestBody @Valid request: CreateAdminGatheringRequest,
	): ApiResponse<CreateAdminGatheringResponse> =
		ApiResponse.success(CreateAdminGatheringResponse.of(createAdminGatheringUseCase.create(request.toCommand())))
}
