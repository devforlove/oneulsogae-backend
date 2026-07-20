package com.org.oneulsogae.core.payments.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinCheckoutUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import com.org.oneulsogae.core.payments.command.application.port.`in`.CompleteCoinPurchaseUseCase
import com.org.oneulsogae.core.payments.command.application.port.`in`.command.CompleteCoinPurchaseCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompleteCoinPurchaseResult
import com.org.oneulsogae.core.payments.command.application.port.out.PaymentConfirmResult
import com.org.oneulsogae.core.payments.command.application.port.out.PaymentGatewayPort
import com.org.oneulsogae.core.payments.command.application.port.out.SaveCoinPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.UpdateCoinPaymentStatusPort
import com.org.oneulsogae.core.payments.command.domain.CoinPayment
import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import org.springframework.stereotype.Service

/**
 * [CompleteCoinPurchaseUseCase] 구현. (오케스트레이터 — 외부 호출을 포함해 클래스 트랜잭션을 두지 않는다)
 * ① PENDING 결제 기록 선저장(자기 트랜잭션): paymentKey를 승인 전에 durable하게 남긴다.
 * ② PG 최종 승인(PaymentGatewayPort.confirm, 트랜잭션 밖).
 * ③ 성공이면 코인을 즉시 적립(AcquireCoinUseCase, 자기 트랜잭션)한 뒤 기록을 APPROVED로 전이한다.
 *    (적립 → APPROVED 순서라 APPROVED 기록은 항상 지급 완료를 뜻한다)
 * ④ 실패면 기록을 FAILED로 전이하고 402. 코인은 승인 성공 후에만 지급하므로 실패 시 보상(회수)이 필요 없다.
 *
 * 잔여 한계: confirm 성공 후 적립이 예외를 던지면 기록이 PENDING으로 남아 "청구됐는데 미지급"이 될 수 있다(수동 대사 대상).
 * paymentKey UNIQUE로 이중 제출의 이중 기록은 막지만, 위반 시 우아한 재생은 범위 밖이다.
 */
@Service
class CompleteCoinPurchaseService(
	private val getCoinCheckoutUseCase: GetCoinCheckoutUseCase,
	private val paymentGatewayPort: PaymentGatewayPort,
	private val saveCoinPaymentPort: SaveCoinPaymentPort,
	private val updateCoinPaymentStatusPort: UpdateCoinPaymentStatusPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
) : CompleteCoinPurchaseUseCase {

	override fun complete(userId: Long, command: CompleteCoinPurchaseCommand): CompleteCoinPurchaseResult {
		// 코인 상품 조회(없으면 COIN-004). salePrice가 서버 확정 실결제가.
		val item: CoinItem = getCoinCheckoutUseCase.getCheckout(command.itemId)

		// ① PENDING 결제 기록 선저장 (자기 트랜잭션). paymentKey를 승인 전에 durable하게 남긴다.
		val payment: CoinPayment = saveCoinPaymentPort.save(
			CoinPayment(
				userId = userId,
				itemId = item.id,
				coinAmount = item.coinAmount,
				amount = item.salePrice,
				paymentKey = command.paymentKey,
				orderId = command.orderId,
				status = PaymentStatus.PENDING,
			),
		)

		// ② PG 최종 승인 (트랜잭션 밖).
		val confirmed: PaymentConfirmResult = paymentGatewayPort.confirm(command.paymentKey, command.orderId, item.salePrice)
		if (!confirmed.approved) {
			// ④-실패: 기록을 FAILED로 남기고(사유·이력 보존) 402. 코인 미지급이라 보상 없음.
			updateCoinPaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.FAILED, confirmed.failReason)
			throw BusinessException(PaymentsErrorCode.PAYMENT_CONFIRM_FAILED)
		}

		// ③-성공: 코인을 즉시 적립(원장+잔액 정합)한 뒤 기록을 APPROVED로 전이한다.
		val balance: CoinBalance = acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(amount = item.coinAmount, coinType = CoinGetType.PURCHASE),
		)
		updateCoinPaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.APPROVED)

		return CompleteCoinPurchaseResult(amount = item.salePrice, coinAmount = item.coinAmount, balance = balance.balance)
	}
}
