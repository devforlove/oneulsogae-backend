package com.org.oneulsogae.api.payments

import com.org.oneulsogae.api.payments.request.CompleteCoinPurchaseRequest
import com.org.oneulsogae.api.payments.request.CompletePaymentRequest
import com.org.oneulsogae.api.payments.response.CheckoutResponse
import com.org.oneulsogae.api.payments.response.CoinCheckoutResponse
import com.org.oneulsogae.api.payments.response.CompleteCoinPurchaseResponse
import com.org.oneulsogae.api.payments.response.CompletePaymentResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinCheckoutUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.gathering.query.dto.GatheringDetailView
import com.org.oneulsogae.core.gathering.query.dto.GatheringProductIdentity
import com.org.oneulsogae.core.gathering.query.dto.GatheringScheduleView
import com.org.oneulsogae.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import com.org.oneulsogae.core.payments.command.application.port.`in`.CompleteCoinPurchaseUseCase
import com.org.oneulsogae.core.payments.command.application.port.`in`.CompletePaymentUseCase
import com.org.oneulsogae.core.payments.query.dto.CheckoutView
import com.org.oneulsogae.core.payments.query.service.port.`in`.GetCheckoutUseCase
import com.org.oneulsogae.core.payments.query.service.port.`in`.GetPaymentMethodsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "결제", description = "결제(체크아웃) 화면 데이터 조회·결제완료 접수")
@RestController
@RequestMapping("/payments/v1")
class PaymentsController(
	private val getCheckoutUseCase: GetCheckoutUseCase,
	private val getGatheringsUseCase: GetGatheringsUseCase,
	private val completePaymentUseCase: CompletePaymentUseCase,
	private val getCoinCheckoutUseCase: GetCoinCheckoutUseCase,
	private val completeCoinPurchaseUseCase: CompleteCoinPurchaseUseCase,
	private val getPaymentMethodsUseCase: GetPaymentMethodsUseCase,
) {

	/**
	 * 결제 화면 진입 시 필요한 주문자 정보·상품(모임 일정) 정보·활성 결제수단을 조회한다.
	 * 상품은 productId 하나로 지정하고, gathering 도메인 in-port가 (모임, 일정, 성별)로 해석한다.
	 * 매진은 soldOut 플래그로 내려주고 차단하지 않는다.
	 */
	@Operation(
		summary = "체크아웃 화면 조회",
		description = "결제 화면 진입 시 필요한 주문자 정보(실명·이메일·휴대폰), 상품 정보(모임·일정·정가·실결제가·매진 여부), 활성 결제수단 목록을 조회한다. " +
			"상품은 productId로 지정한다(모임 상세 응답의 schedules[].productId). 본인인증 전 사용자는 주문자 필드가 null일 수 있다. " +
			"상품 없음 404(GATHERING-006), 모임 없음/모집중 아님 404(GATHERING-001), 일정 미매칭 404(PAYMENTS-001).",
	)
	// [모임 기능 미노출] 모임 결제 체크아웃 — 매핑 주석 처리로 라우트 미등록(호출 시 404). 코인 체크아웃(/coin/checkout)은 유지. 재노출 시 주석 해제.
	// @GetMapping("/checkout")
	fun getCheckout(
		@LoginUser user: AuthUser,
		@RequestParam productId: Long,
	): ApiResponse<CheckoutResponse> {
		val checkout: CheckoutView = getCheckoutUseCase.getCheckout(user.id)
		val product: GatheringProductIdentity = getGatheringsUseCase.getProduct(productId)
		val gathering: GatheringDetailView = getGatheringsUseCase.getGathering(product.gatheringId)
		val schedule: GatheringScheduleView = gathering.scheduleOrNull(product.scheduleId)
			?: throw BusinessException(PaymentsErrorCode.CHECKOUT_PRODUCT_NOT_FOUND)
		return ApiResponse.success(CheckoutResponse.of(user.id, checkout, gathering, schedule, product.gender))
	}

	/** 코인 구매 직전 체크아웃 데이터(구매할 코인 아이템 + 구매방법)를 조회한다. */
	@Operation(
		summary = "코인 체크아웃 조회",
		description = "구매할 코인 아이템(itemId)과 활성 구매방법(결제수단) 목록을 반환한다. 코인 상품 없음 404(COIN-004).",
	)
	@GetMapping("/coin/checkout")
	fun getCoinCheckout(
		@LoginUser user: AuthUser,
		@RequestParam itemId: Long,
	): ApiResponse<CoinCheckoutResponse> =
		ApiResponse.success(
			CoinCheckoutResponse.of(
				user.id,
				getCoinCheckoutUseCase.getCheckout(itemId),
				getPaymentMethodsUseCase.getActiveMethods(),
			),
		)

	/**
	 * 코인 구매 결제완료를 접수한다. PENDING 결제 기록으로 paymentKey를 선기록한 뒤 PG 최종 승인(confirm)을 받고,
	 * 성공하면 구매한 코인을 즉시 잔액에 적립한다(모임 좌석과 달리 운영자 승인 없이 즉시 지급).
	 */
	@Operation(
		summary = "코인 결제완료 접수",
		description = "코인 구매 결제 완료를 접수해 PG 최종 승인(confirm)을 받고, 성공 시 구매한 코인을 즉시 잔액에 적립한다. " +
			"상품은 itemId로 지정한다(코인 체크아웃의 item.id). 실결제가는 서버가 상품 할인가로 확정한다. " +
			"paymentKey 기준 멱등: 이미 승인된 키의 재접수(성공 URL 새로고침 등)는 재지급 없이 기존 결제 내역과 현재 잔액을 200으로 반환한다. " +
			"코인 상품 없음 404(COIN-004), 결제 승인 실패 402(PAYMENTS-004), 이미 접수돼 승인 대기 중이거나 타인의 paymentKey 409(PAYMENTS-005).",
	)
	@PostMapping("/coin/complete")
	fun completeCoinPurchase(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CompleteCoinPurchaseRequest,
	): ApiResponse<CompleteCoinPurchaseResponse> =
		ApiResponse.success(CompleteCoinPurchaseResponse.of(completeCoinPurchaseUseCase.complete(user.id, request.toCommand())))

	/**
	 * 결제완료를 접수한다. 본인 프로필 성별을 강제해 좌석을 확보(승인대기 PENDING)한 뒤 PG 최종 승인(confirm)을 받고
	 * 서버 확정가로 결제 기록을 남긴다. 운영자 승인 후 참가(JOINED)로 전환된다.
	 */
	@Operation(
		summary = "결제완료 접수",
		description = "결제 완료를 접수해 좌석을 확보(승인대기로 등록)하고 PG 최종 승인(confirm)까지 받아야 결제 기록을 남긴다. " +
			"상품은 productId로 지정한다(모임 상세 응답의 schedules[].productId). " +
			"성별 여분·얼리버드 여분을 접수 시점에 차감한다. " +
			"상품 없음 404(GATHERING-006), 타성별 상품 400(PAYMENTS-003), 판매 중 아님 409(GATHERING-003), " +
			"매진 409(GATHERING-004), 중복 접수 409(GATHERING-005), 성별 미확정 400(PAYMENTS-002), " +
			"결제 승인 실패 402(PAYMENTS-004).",
	)
	// [모임 기능 미노출] 모임 결제완료 접수 — 매핑 주석 처리로 라우트 미등록(호출 시 404). 재노출 시 주석 해제.
	// @PostMapping("/complete")
	fun complete(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CompletePaymentRequest,
	): ApiResponse<CompletePaymentResponse> =
		ApiResponse.success(CompletePaymentResponse.of(completePaymentUseCase.complete(user.id, request.toCommand())))
}
