package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.api.lounge.response.AcceptLoungeChatResponse
import com.org.oneulsogae.api.lounge.response.LoungeChatRequestPageResponse
import com.org.oneulsogae.api.lounge.response.LoungeChatRequestResponse
import com.org.oneulsogae.api.lounge.response.SentLoungeChatRequestPageResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.AcceptLoungeChatUseCase
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
 * - GET /lounge/v1/chat-requests/received: 내가 받은 대화 신청 목록을 최신순으로 조회한다. (내가 쓴 모든 셀소 합산)
 * - GET /lounge/v1/chat-requests/sent: 내가 보낸 대화 신청 목록을 최신순으로 조회한다.
 * - POST /lounge/v1/chat-requests/{requestId}/accept: 받은 대화 신청을 수락해 채팅방을 연다. (코인 차감)
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 대화 신청", description = "라운지 셀소 대화 신청·수락 엔드포인트 (인증 필요)")
class LoungeChatRequestController(
	private val requestLoungeChatUseCase: RequestLoungeChatUseCase,
	private val getLoungeChatRequestsUseCase: GetLoungeChatRequestsUseCase,
	private val acceptLoungeChatUseCase: AcceptLoungeChatUseCase,
) {

	/** 셀소 작성자에게 대화를 신청한다. 신청 코인(32)이 차감된다. */
	@Operation(
		summary = "대화 신청",
		description = "라운지 셀소 상세에서 작성자에게 대화를 신청한다. 신청 코인 32가 차감되고 신청은 PENDING 상태로 저장된다. 본인 글이면 400(LOUNGE-009), 이미 신청한 글이면 409(LOUNGE-010), 글이 없으면 404(LOUNGE-008)를 반환한다. 동시 요청이 겹치면 409(LOCK-001)를 반환한다.",
	)
	@PostMapping("/self-intro-posts/{postId}/chat-requests")
	fun requestChat(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
	): ApiResponse<LoungeChatRequestResponse> =
		ApiResponse.success(LoungeChatRequestResponse.of(requestLoungeChatUseCase.request(user.id, postId)))

	/** 내가 받은 대화 신청 목록을 최신순 한 페이지 조회한다. */
	@Operation(
		summary = "받은 대화 신청 목록 조회",
		description = "내가 쓴 셀소에 온 대화 신청을 최신순으로 20개씩 내려준다. 내가 쓴 모든 셀소를 합산한다. 각 항목은 신청 식별자(requestId)·글 식별자(postId)·상대방(partnerUserId·partnerNickname·partnerGender·partnerAge·partnerProfileImageCode·partnerActivityArea·partnerJob·partnerCompanyName — 받은 목록에서는 신청자)·상태(PENDING/ACCEPTED)·신청 시각을 담는다. 수락으로 생긴 채팅방은 채팅방 목록에서 확인한다(이 응답에는 싣지 않는다). 수락 버튼의 비용 안내에 쓸 acceptCoinAmount(수락 시 차감되는 코인 수)는 신청마다 다르지 않으므로 항목이 아니라 응답 루트에 한 번만 담는다. 다음 페이지는 응답의 nextCursor를 cursor 파라미터로 그대로 넘겨 조회한다(hasNext=false면 마지막 페이지).",
	)
	@GetMapping("/chat-requests/received")
	fun getReceivedChatRequests(
		@LoginUser user: AuthUser,
		@RequestParam("cursor", required = false) cursor: Long?,
	): ApiResponse<LoungeChatRequestPageResponse> =
		ApiResponse.success(LoungeChatRequestPageResponse.of(getLoungeChatRequestsUseCase.getReceived(user.id, cursor)))

	/** 내가 보낸 대화 신청 목록을 최신순 한 페이지 조회한다. */
	@Operation(
		summary = "보낸 대화 신청 목록 조회",
		description = "내가 남의 셀소에 보낸 대화 신청을 최신순으로 20개씩 내려준다. 각 항목은 신청 식별자(requestId)·글 식별자(postId)·상대방(partnerUserId·partnerNickname·partnerGender·partnerAge·partnerProfileImageCode·partnerActivityArea·partnerJob·partnerCompanyName — 보낸 목록에서는 글 작성자)·상태(PENDING/ACCEPTED)·신청 시각을 담는다. 수락으로 생긴 채팅방은 채팅방 목록에서 확인한다(이 응답에는 싣지 않는다). 보낸 신청은 내가 수락하는 것이 아니라 수락 비용을 싣지 않는다. 다음 페이지는 응답의 nextCursor를 cursor 파라미터로 그대로 넘겨 조회한다(hasNext=false면 마지막 페이지).",
	)
	@GetMapping("/chat-requests/sent")
	fun getSentChatRequests(
		@LoginUser user: AuthUser,
		@RequestParam("cursor", required = false) cursor: Long?,
	): ApiResponse<SentLoungeChatRequestPageResponse> =
		ApiResponse.success(SentLoungeChatRequestPageResponse.of(getLoungeChatRequestsUseCase.getSent(user.id, cursor)))

	/** 내 셀소에 온 대화 신청을 수락해 채팅방을 연다. 수락 코인(32)이 차감된다. */
	@Operation(
		summary = "대화 신청 수락",
		description = "내가 쓴 셀소에 온 대화 신청을 수락한다. 수락 코인 32가 차감되고 신청자와의 채팅방이 생성되며 응답의 chatRoomId로 바로 진입할 수 있다. 같은 글의 신청을 여러 건 수락할 수 있다. 내 글에 온 신청이 아니면 403(LOUNGE-011), 이미 수락했으면 409(LOUNGE-013), 신청이 없으면 404(LOUNGE-012)를 반환한다. 동시 요청이 겹치면 409(LOCK-001)를 반환한다.",
	)
	@PostMapping("/chat-requests/{requestId}/accept")
	fun acceptChatRequest(
		@LoginUser user: AuthUser,
		@PathVariable("requestId") requestId: Long,
	): ApiResponse<AcceptLoungeChatResponse> =
		ApiResponse.success(AcceptLoungeChatResponse.of(acceptLoungeChatUseCase.accept(user.id, requestId)))
}
