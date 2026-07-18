package com.org.meeple.api.admin

import com.org.meeple.admin.memberverification.command.application.port.`in`.ReviewMemberVerificationUseCase
import com.org.meeple.admin.memberverification.query.service.port.`in`.GetAdminMemberVerificationsUseCase
import com.org.meeple.api.admin.request.AdminRejectMemberVerificationRequest
import com.org.meeple.api.admin.response.AdminMemberVerificationDetailResponse
import com.org.meeple.api.admin.response.AdminMemberVerificationPageResponse
import com.org.meeple.common.gathering.MemberVerificationStatus
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
 * 어드민 멤버 인증(본인인증) 심사 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 유저가 제출한 멤버 인증(얼굴·신분증·서류 + 직업 정보)을 조회·승인한다.
 */
@Tag(name = "어드민 멤버 인증", description = "어드민 백오피스 멤버 인증(본인인증) 심사. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/member-verifications")
class AdminMemberVerificationController(
	private val getAdminMemberVerificationsUseCase: GetAdminMemberVerificationsUseCase,
	private val reviewMemberVerificationUseCase: ReviewMemberVerificationUseCase,
) {

	@Operation(
		summary = "멤버 인증 목록 조회",
		description = "멤버 인증을 최신순으로 page(0부터)·size 페이징 조회한다. status(PENDING/APPROVED/REJECTED) 생략 시 전체. 사진 열람 URL은 상세에서만 내려간다.",
	)
	@GetMapping
	fun verifications(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: MemberVerificationStatus?,
	): ApiResponse<AdminMemberVerificationPageResponse> =
		ApiResponse.success(
			AdminMemberVerificationPageResponse.of(
				getAdminMemberVerificationsUseCase.getVerifications(page, size, status),
			),
		)

	@Operation(
		summary = "멤버 인증 상세 조회",
		description = "멤버 인증 한 건을 id로 조회한다. 없으면 404(MEMBER-VERIFICATION-001). 사진 3종(얼굴·신분증·서류)의 열람 URL은 일정 시간 유효한 presigned URL이다.",
	)
	@GetMapping("/{id}")
	fun verification(
		@PathVariable id: Long,
	): ApiResponse<AdminMemberVerificationDetailResponse> =
		ApiResponse.success(
			AdminMemberVerificationDetailResponse.of(
				getAdminMemberVerificationsUseCase.getVerification(id),
			),
		)

	@Operation(
		summary = "멤버 인증 승인",
		description = "멤버 인증을 승인(APPROVED)한다. 없으면 404(MEMBER-VERIFICATION-001).",
	)
	@PostMapping("/{id}/approve")
	fun approve(
		@PathVariable id: Long,
	): ApiResponse<Unit> {
		reviewMemberVerificationUseCase.approve(id)
		return ApiResponse.success()
	}

	@Operation(
		summary = "멤버 인증 반려",
		description = "멤버 인증을 반려(REJECTED)하고 사유(선택, 최대 500자)를 저장한다. 없으면 404(MEMBER-VERIFICATION-001).",
	)
	@PostMapping("/{id}/reject")
	fun reject(
		@PathVariable id: Long,
		@RequestBody(required = false) @Valid request: AdminRejectMemberVerificationRequest?,
	): ApiResponse<Unit> {
		reviewMemberVerificationUseCase.reject(id, request?.reason)
		return ApiResponse.success()
	}
}
