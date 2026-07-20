package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.companyverification.command.application.port.`in`.ReviewCompanyImageVerificationUseCase
import com.org.oneulsogae.admin.companyverification.query.service.port.`in`.GetAdminCompanyVerificationsUseCase
import com.org.oneulsogae.api.admin.request.AdminApproveCompanyVerificationRequest
import com.org.oneulsogae.api.admin.request.AdminRejectCompanyVerificationRequest
import com.org.oneulsogae.api.admin.response.AdminCompanyVerificationDetailResponse
import com.org.oneulsogae.api.admin.response.AdminCompanyVerificationPageResponse
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.core.common.response.ApiResponse
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
 * 어드민 회사 이미지 인증 조회 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 */
@Tag(name = "어드민 회사 인증", description = "어드민 백오피스 직장 서류 인증 조회. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/company-image-verifications")
class AdminCompanyVerificationController(
	private val getAdminCompanyVerificationsUseCase: GetAdminCompanyVerificationsUseCase,
	private val reviewCompanyImageVerificationUseCase: ReviewCompanyImageVerificationUseCase,
) {

	@Operation(
		summary = "회사 이미지 인증 목록 조회",
		description = "직장 서류 인증을 최신순으로 page(0부터)·size 페이징 조회한다. status(PENDING/APPROVED/REJECTED) 생략 시 전체. 각 항목의 imageUrl은 일정 시간 유효한 열람용 presigned URL이다.",
	)
	@GetMapping
	fun verifications(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: CompanyImageVerificationStatus?,
	): ApiResponse<AdminCompanyVerificationPageResponse> =
		ApiResponse.success(
			AdminCompanyVerificationPageResponse.of(
				getAdminCompanyVerificationsUseCase.getVerifications(page, size, status),
			),
		)

	@Operation(
		summary = "회사 이미지 인증 상세 조회",
		description = "직장 서류 인증 한 건을 id로 조회한다. 없으면 404(COMPANY-IMAGE-001). imageUrl은 일정 시간 유효한 열람용 presigned URL이다.",
	)
	@GetMapping("/{id}")
	fun verification(
		@PathVariable id: Long,
	): ApiResponse<AdminCompanyVerificationDetailResponse> =
		ApiResponse.success(
			AdminCompanyVerificationDetailResponse.of(
				getAdminCompanyVerificationsUseCase.getVerification(id),
			),
		)

	@Operation(
		summary = "회사 이미지 인증 승인",
		description = "인증을 승인(APPROVED)하고 어드민이 기입한 회사명을 유저 프로필에 확정한다. 없으면 404(COMPANY-IMAGE-001), 회사명이 비면 400.",
	)
	@PostMapping("/{id}/approve")
	fun approve(
		@PathVariable id: Long,
		@RequestBody @Valid request: AdminApproveCompanyVerificationRequest,
	): ApiResponse<Unit> {
		reviewCompanyImageVerificationUseCase.approve(id, request.companyName!!)
		return ApiResponse.success()
	}

	@Operation(
		summary = "회사 이미지 인증 반려",
		description = "인증을 반려(REJECTED)하고 사유(선택)를 저장한다. 없으면 404(COMPANY-IMAGE-001).",
	)
	@PostMapping("/{id}/reject")
	fun reject(
		@PathVariable id: Long,
		@RequestBody(required = false) @Valid request: AdminRejectCompanyVerificationRequest?,
	): ApiResponse<Unit> {
		reviewCompanyImageVerificationUseCase.reject(id, request?.reason)
		return ApiResponse.success()
	}
}
