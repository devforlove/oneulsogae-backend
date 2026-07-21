package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.api.lounge.response.LoungeChatRequestPageResponse
import com.org.oneulsogae.api.lounge.response.LoungeChatRequestResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.RequestLoungeChatUseCase
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetLoungeChatRequestsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 라운지 셀소 대화 신청 엔드포인트. (인증 필요)
 * - POST /lounge/v1/self-intro-posts/{postId}/chat-requests: 셀소 작성자에게 대화를 신청한다. (코인 차감)
 * - GET /lounge/v1/self-intro-posts/{postId}/chat-requests: 내 셀소에 온 대화 신청 목록을 최신순으로 조회한다.
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 대화 신청", description = "라운지 셀소 대화 신청·수락 엔드포인트 (인증 필요)")
class LoungeChatRequestController(
	private val requestLoungeChatUseCase: RequestLoungeChatUseCase,
	private val getLoungeChatRequestsUseCase: GetLoungeChatRequestsUseCase,
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

	/** 내 셀소에 온 대화 신청 목록을 최신순 한 페이지 조회한다. */
	@Operation(
		summary = "받은 대화 신청 목록 조회",
		description = "내가 쓴 셀소에 온 대화 신청을 최신순으로 20개씩 내려준다. 각 항목은 신청 식별자(requestId)·신청자(userId·닉네임·성별·만 나이)·상태(PENDING/ACCEPTED)·수락으로 생긴 채팅방 id(수락 전이면 null)·신청 시각을 담는다. 다음 페이지는 응답의 nextCursor를 cursor 파라미터로 그대로 넘겨 조회한다(hasNext=false면 마지막 페이지). 내 글이 아니면 403(LOUNGE-011), 글이 없으면 404(LOUNGE-008)를 반환한다.",
	)
	@GetMapping("/self-intro-posts/{postId}/chat-requests")
	fun getChatRequests(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
		@RequestParam("cursor", required = false) cursor: Long?,
	): ApiResponse<LoungeChatRequestPageResponse> =
		ApiResponse.success(
			LoungeChatRequestPageResponse.of(getLoungeChatRequestsUseCase.getRequests(user.id, postId, cursor)),
		)
}
