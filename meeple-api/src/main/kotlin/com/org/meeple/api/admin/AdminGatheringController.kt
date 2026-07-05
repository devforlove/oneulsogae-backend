package com.org.meeple.api.admin

import com.org.meeple.admin.gathering.command.application.port.`in`.CreateAdminGatheringUseCase
import com.org.meeple.admin.gathering.query.service.port.`in`.GetAdminGatheringsUseCase
import com.org.meeple.api.admin.request.CreateAdminGatheringRequest
import com.org.meeple.api.admin.response.AdminGatheringDetailResponse
import com.org.meeple.api.admin.response.AdminGatheringPageResponse
import com.org.meeple.api.admin.response.CreateAdminGatheringResponse
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 모임 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * - GET /: 최신순 page·size 페이징 목록 (소개·참가비 상세 제외).
 * - GET /{id}: 상세(소개·참가비 상세 포함). 없으면 404(GATHER-008).
 * - POST /: 종류·제목·소개·지역·일시·정원·참가비(성별·티어)로 모임 생성. (운영 생성 → user_id null)
 */
@Tag(name = "어드민 모임", description = "어드민 백오피스 모임 조회·등록. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/gatherings")
class AdminGatheringController(
	private val createAdminGatheringUseCase: CreateAdminGatheringUseCase,
	private val getAdminGatheringsUseCase: GetAdminGatheringsUseCase,
) {

	@Operation(
		summary = "모임 목록 조회",
		description = "모임을 저장 날짜(생성 시각) 최신순으로 page(0부터)·size 페이징 조회한다. " +
			"status(모임 상태)·type(모임 종류)으로 필터할 수 있다(없으면 전체). 목록 항목은 소개·참가비 상세를 포함하지 않는다.",
	)
	@GetMapping
	fun gatherings(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: GatheringStatus?,
		@RequestParam(required = false) type: GatheringType?,
	): ApiResponse<AdminGatheringPageResponse> =
		ApiResponse.success(AdminGatheringPageResponse.of(getAdminGatheringsUseCase.getGatherings(page, size, status, type)))

	@Operation(
		summary = "모임 상세 조회",
		description = "모임 한 건을 id로 조회한다(소개·참가비 상세 포함). 없으면 404(GATHER-008).",
	)
	@GetMapping("/{id}")
	fun gathering(
		@PathVariable id: Long,
	): ApiResponse<AdminGatheringDetailResponse> =
		ApiResponse.success(AdminGatheringDetailResponse.of(getAdminGatheringsUseCase.getGathering(id)))

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
