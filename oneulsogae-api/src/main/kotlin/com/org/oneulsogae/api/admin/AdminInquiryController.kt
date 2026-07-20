package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.inquiry.command.application.port.`in`.AnswerInquiryUseCase
import com.org.oneulsogae.admin.inquiry.query.service.port.`in`.GetAdminInquiriesUseCase
import com.org.oneulsogae.api.admin.request.AnswerInquiryRequest
import com.org.oneulsogae.api.admin.response.AdminInquiryDetailResponse
import com.org.oneulsogae.api.admin.response.AdminInquiryPageResponse
import com.org.oneulsogae.common.inquiry.InquiryStatus
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
 * 어드민 문의 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * - GET /: 최신순 page·size 페이징 목록(본문 제외). status로 상태 필터.
 * - GET /{id}: 상세(본문·답변 포함). 없으면 404(INQUIRY-001).
 */
@Tag(name = "어드민 문의", description = "어드민 백오피스 문의 조회·답변. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/inquiries")
class AdminInquiryController(
	private val getAdminInquiriesUseCase: GetAdminInquiriesUseCase,
	private val answerInquiryUseCase: AnswerInquiryUseCase,
) {

	@Operation(
		summary = "문의 목록 조회",
		description = "문의를 접수 시각 최신순으로 page(0부터)·size 페이징 조회한다. status(PENDING/ANSWERED)로 상태를 필터할 수 있다. 목록 항목은 본문(message)을 포함하지 않는다.",
	)
	@GetMapping
	fun inquiries(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: InquiryStatus?,
	): ApiResponse<AdminInquiryPageResponse> =
		ApiResponse.success(AdminInquiryPageResponse.of(getAdminInquiriesUseCase.getInquiries(page, size, status)))

	@Operation(
		summary = "문의 상세 조회",
		description = "문의 한 건을 id로 조회한다(본문·답변 포함). 없으면 404(INQUIRY-001).",
	)
	@GetMapping("/{id}")
	fun inquiry(
		@PathVariable id: Long,
	): ApiResponse<AdminInquiryDetailResponse> =
		ApiResponse.success(AdminInquiryDetailResponse.of(getAdminInquiriesUseCase.getInquiry(id)))

	@Operation(
		summary = "문의 답변",
		description = "PENDING 문의에 답변한다(status=ANSWERED 전이). 없으면 404(INQUIRY-001), 이미 답변됐으면 409(INQUIRY-002), 답변이 비면 400.",
	)
	@PostMapping("/{id}/answer")
	fun answer(
		@PathVariable id: Long,
		@RequestBody @Valid request: AnswerInquiryRequest,
	): ApiResponse<Unit> {
		answerInquiryUseCase.answer(request.toCommand(id))
		return ApiResponse.success()
	}
}
