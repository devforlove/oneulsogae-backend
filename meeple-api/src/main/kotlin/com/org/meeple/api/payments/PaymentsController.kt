package com.org.meeple.api.payments

import com.org.meeple.api.payments.request.CompletePaymentRequest
import com.org.meeple.api.payments.response.CheckoutResponse
import com.org.meeple.api.payments.response.CompletePaymentResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.meeple.core.payments.PaymentsErrorCode
import com.org.meeple.core.payments.command.application.port.`in`.CompletePaymentUseCase
import com.org.meeple.core.payments.query.dto.CheckoutView
import com.org.meeple.core.payments.query.service.port.`in`.GetCheckoutUseCase
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
) {

	/**
	 * 결제 화면 진입 시 필요한 주문자 정보·상품(모임 일정) 정보·활성 결제수단을 조회한다.
	 * 상품은 gathering 도메인 in-port로 조합한다. 매진은 soldOut 플래그로 내려주고 차단하지 않는다.
	 */
	@Operation(
		summary = "체크아웃 화면 조회",
		description = "결제 화면 진입 시 필요한 주문자 정보(실명·이메일·휴대폰), 상품 정보(모임·일정·정가·실결제가·매진 여부), 활성 결제수단 목록을 조회한다. 본인인증 전 사용자는 주문자 필드가 null일 수 있다.",
	)
	@GetMapping("/checkout")
	fun getCheckout(
		@LoginUser user: AuthUser,
		@RequestParam gatheringId: Long,
		@RequestParam scheduleId: Long,
		@RequestParam gender: Gender,
	): ApiResponse<CheckoutResponse> {
		val checkout: CheckoutView = getCheckoutUseCase.getCheckout(user.id)
		val gathering: GatheringDetailView = getGatheringsUseCase.getGathering(gatheringId)
		val schedule: GatheringScheduleView = gathering.scheduleOrNull(scheduleId)
			?: throw BusinessException(PaymentsErrorCode.CHECKOUT_PRODUCT_NOT_FOUND)
		return ApiResponse.success(CheckoutResponse.of(checkout, gathering, schedule, gender))
	}

	/**
	 * 결제완료를 접수한다. 무검증 접수: 본인 프로필 성별을 강제해 참가를 승인대기(PENDING)로 등록하고
	 * 서버 확정가로 결제 기록을 남긴다. 운영자 승인 후 참가(JOINED)로 전환된다.
	 */
	@Operation(
		summary = "결제완료 접수",
		description = "결제 완료를 접수해 참가를 승인대기로 등록한다. 성별 여분·얼리버드 여분을 접수 시점에 차감한다. " +
			"일정 없음 404(GATHERING-002), 판매 중 아님 409(GATHERING-003), 매진 409(GATHERING-004), " +
			"중복 접수 409(GATHERING-005), 성별 미확정 400(PAYMENTS-002).",
	)
	@PostMapping("/complete")
	fun complete(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CompletePaymentRequest,
	): ApiResponse<CompletePaymentResponse> =
		ApiResponse.success(CompletePaymentResponse.of(completePaymentUseCase.complete(user.id, request.toCommand())))
}
