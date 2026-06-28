package com.org.meeple.api.notice

import com.org.meeple.api.notice.request.CreateNoticeRequest
import com.org.meeple.api.notice.response.CreateNoticeResponse
import com.org.meeple.api.notice.response.NoticePageResponse
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.notice.command.application.port.`in`.CreateNoticeUseCase
import com.org.meeple.core.notice.query.service.port.`in`.GetNoticesUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 공지 엔드포인트.
 * - POST /: 제목·설명으로 공지를 생성한다.
 * - GET /: 공지를 저장 날짜 최신순으로 page·size 페이징해 조회한다.
 */
@Tag(name = "공지", description = "공지사항 API. 공지를 등록하고 목록을 조회한다.")
@RestController
@RequestMapping("/notices/v1")
class NoticeController(
	private val createNoticeUseCase: CreateNoticeUseCase,
	private val getNoticesUseCase: GetNoticesUseCase,
) {

	@Operation(summary = "공지 생성", description = "제목과 설명으로 공지를 생성한다. 저장 날짜는 생성 시각으로 자동 기록된다.")
	@PostMapping
	fun create(
		@RequestBody @Valid request: CreateNoticeRequest,
	): ApiResponse<CreateNoticeResponse> =
		ApiResponse.success(CreateNoticeResponse.of(createNoticeUseCase.create(request.toCommand())))

	@Operation(summary = "공지 목록 조회", description = "공지를 저장 날짜(생성 시각) 최신순으로 page(0부터)·size 단위로 페이징해 조회한다.")
	@GetMapping
	fun notices(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ApiResponse<NoticePageResponse> =
		ApiResponse.success(NoticePageResponse.of(getNoticesUseCase.getNotices(page, size)))
}
