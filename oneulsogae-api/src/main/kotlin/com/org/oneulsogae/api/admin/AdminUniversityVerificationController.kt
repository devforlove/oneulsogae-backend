package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.universityverification.command.application.port.`in`.ReviewUniversityImageVerificationUseCase
import com.org.oneulsogae.admin.universityverification.query.service.port.`in`.GetAdminUniversityVerificationsUseCase
import com.org.oneulsogae.api.admin.request.AdminApproveUniversityVerificationRequest
import com.org.oneulsogae.api.admin.request.AdminRejectUniversityVerificationRequest
import com.org.oneulsogae.api.admin.response.AdminUniversityVerificationDetailResponse
import com.org.oneulsogae.api.admin.response.AdminUniversityVerificationPageResponse
import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
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
 * 어드민 학교 이미지 인증 조회 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 */
@Tag(name = "어드민 학교 인증", description = "어드민 백오피스 학교 서류 인증 조회. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/university-image-verifications")
class AdminUniversityVerificationController(
	private val getAdminUniversityVerificationsUseCase: GetAdminUniversityVerificationsUseCase,
	private val reviewUniversityImageVerificationUseCase: ReviewUniversityImageVerificationUseCase,
) {

	@Operation(
		summary = "학교 이미지 인증 목록 조회",
		description = "학교 서류 인증을 최신순으로 page(0부터)·size 페이징 조회한다. status(PENDING/APPROVED/REJECTED) 생략 시 전체. 각 항목의 imageUrl은 일정 시간 유효한 열람용 presigned URL이다.",
	)
	@GetMapping
	fun verifications(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: UniversityImageVerificationStatus?,
	): ApiResponse<AdminUniversityVerificationPageResponse> =
		ApiResponse.success(
			AdminUniversityVerificationPageResponse.of(
				getAdminUniversityVerificationsUseCase.getVerifications(page, size, status),
			),
		)

	@Operation(
		summary = "학교 이미지 인증 상세 조회",
		description = "학교 서류 인증 한 건을 id로 조회한다. 없으면 404(UNIVERSITY-IMAGE-001). imageUrl은 일정 시간 유효한 열람용 presigned URL이다.",
	)
	@GetMapping("/{id}")
	fun verification(
		@PathVariable id: Long,
	): ApiResponse<AdminUniversityVerificationDetailResponse> =
		ApiResponse.success(
			AdminUniversityVerificationDetailResponse.of(
				getAdminUniversityVerificationsUseCase.getVerification(id),
			),
		)

	@Operation(
		summary = "학교 이미지 인증 승인",
		description = "인증을 승인(APPROVED)하고 어드민이 기입한 학교명을 유저 프로필에 확정한다. 없으면 404(UNIVERSITY-IMAGE-001), 학교명이 비면 400.",
	)
	@PostMapping("/{id}/approve")
	fun approve(
		@PathVariable id: Long,
		@RequestBody @Valid request: AdminApproveUniversityVerificationRequest,
	): ApiResponse<Unit> {
		reviewUniversityImageVerificationUseCase.approve(id, request.universityName!!)
		return ApiResponse.success()
	}

	@Operation(
		summary = "학교 이미지 인증 반려",
		description = "인증을 반려(REJECTED)하고 사유(선택)를 저장한다. 없으면 404(UNIVERSITY-IMAGE-001).",
	)
	@PostMapping("/{id}/reject")
	fun reject(
		@PathVariable id: Long,
		@RequestBody(required = false) @Valid request: AdminRejectUniversityVerificationRequest?,
	): ApiResponse<Unit> {
		reviewUniversityImageVerificationUseCase.reject(id, request?.reason)
		return ApiResponse.success()
	}
}
