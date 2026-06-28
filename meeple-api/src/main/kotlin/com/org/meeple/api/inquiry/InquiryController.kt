package com.org.meeple.api.inquiry

import com.org.meeple.api.inquiry.request.CreateInquiryRequest
import com.org.meeple.api.inquiry.response.CreateInquiryResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.inquiry.command.application.port.`in`.CreateInquiryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "문의", description = "고객센터 문의하기 API. 1:1 문의를 접수한다.")
@RestController
@RequestMapping("/inquiries/v1")
class InquiryController(
	private val createInquiryUseCase: CreateInquiryUseCase,
) {

	@Operation(summary = "문의 생성", description = "로그인 사용자가 문의 유형·답변 이메일·내용으로 1:1 문의를 접수한다.")
	@PostMapping
	fun create(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CreateInquiryRequest,
	): ApiResponse<CreateInquiryResponse> =
		ApiResponse.success(CreateInquiryResponse.of(createInquiryUseCase.create(request.toCommand(user.id))))
}
