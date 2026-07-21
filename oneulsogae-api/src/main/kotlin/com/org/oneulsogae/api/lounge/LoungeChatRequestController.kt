package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.api.lounge.response.LoungeChatRequestResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.RequestLoungeChatUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 라운지 셀소 대화 신청 엔드포인트. (인증 필요)
 * - POST /lounge/v1/self-intro-posts/{postId}/chat-requests: 셀소 작성자에게 대화를 신청한다. (코인 차감)
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 대화 신청", description = "라운지 셀소 대화 신청·수락 엔드포인트 (인증 필요)")
class LoungeChatRequestController(
	private val requestLoungeChatUseCase: RequestLoungeChatUseCase,
) {

	/** 셀소 작성자에게 대화를 신청한다. 신청 코인(32)이 차감된다. */
	@Operation(
		summary = "대화 신청",
		description = "라운지 셀소 상세에서 작성자에게 대화를 신청한다. 신청 코인 32가 차감되고 신청은 PENDING 상태로 저장된다. 본인 글이면 400(LOUNGE-009), 이미 신청한 글이면 409(LOUNGE-010), 글이 없으면 404(LOUNGE-008)를 반환한다.",
	)
	@PostMapping("/self-intro-posts/{postId}/chat-requests")
	fun requestChat(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
	): ApiResponse<LoungeChatRequestResponse> =
		ApiResponse.success(LoungeChatRequestResponse.of(requestLoungeChatUseCase.request(user.id, postId)))
}
