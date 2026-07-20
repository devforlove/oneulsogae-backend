package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.notice.command.application.port.`in`.CreateAdminNoticeUseCase
import com.org.oneulsogae.admin.notice.query.service.port.`in`.GetAdminNoticesUseCase
import com.org.oneulsogae.api.admin.request.CreateAdminNoticeRequest
import com.org.oneulsogae.api.admin.response.AdminNoticeDetailResponse
import com.org.oneulsogae.api.admin.response.AdminNoticePageResponse
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
 * 어드민 공지 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * - GET /: 최신순 page·size 페이징 목록 (본문 제외).
 * - GET /{id}: 상세(본문 포함). 없으면 404(NOTICE-001).
 * - POST /: 제목·설명으로 공지 추가.
 */
@Tag(name = "어드민 공지", description = "어드민 백오피스 공지 조회·등록. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/notices")
class AdminNoticeController(
	private val getAdminNoticesUseCase: GetAdminNoticesUseCase,
	private val createAdminNoticeUseCase: CreateAdminNoticeUseCase,
) {

	@Operation(
		summary = "공지 목록 조회",
		description = "공지를 저장 날짜(생성 시각) 최신순으로 page(0부터)·size 페이징 조회한다. 목록 항목은 본문(description)을 포함하지 않는다.",
	)
	@GetMapping
	fun notices(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ApiResponse<AdminNoticePageResponse> =
		ApiResponse.success(AdminNoticePageResponse.of(getAdminNoticesUseCase.getNotices(page, size)))

	@Operation(
		summary = "공지 상세 조회",
		description = "공지 한 건을 id로 조회한다(본문 포함). 없으면 404(NOTICE-001).",
	)
	@GetMapping("/{id}")
	fun notice(
		@PathVariable id: Long,
	): ApiResponse<AdminNoticeDetailResponse> =
		ApiResponse.success(AdminNoticeDetailResponse.of(getAdminNoticesUseCase.getNotice(id)))

	@Operation(
		summary = "공지 추가",
		description = "제목과 설명으로 공지를 등록한다. 저장 날짜는 생성 시각으로 자동 기록된다. 제목·설명이 비면 400.",
	)
	@PostMapping
	fun create(
		@RequestBody @Valid request: CreateAdminNoticeRequest,
	): ApiResponse<Unit> {
		createAdminNoticeUseCase.create(request.toCommand())
		return ApiResponse.success()
	}
}
