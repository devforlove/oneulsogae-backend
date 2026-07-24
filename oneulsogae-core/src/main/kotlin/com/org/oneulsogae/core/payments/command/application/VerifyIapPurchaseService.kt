package com.org.oneulsogae.core.payments.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.payments.command.application.port.`in`.VerifyIapPurchaseUseCase
import com.org.oneulsogae.core.payments.command.application.port.`in`.command.VerifyIapPurchaseCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.VerifyIapPurchaseResult
import com.org.oneulsogae.core.payments.command.application.port.out.StoreReceiptVerifierPort
import com.org.oneulsogae.core.payments.command.application.port.out.VerifiedReceipt
import org.springframework.stereotype.Service

/**
 * [VerifyIapPurchaseUseCase] 구현. 스토어 인앱결제 영수증을 검증하고 코인을 적립한다.
 * ① 영수증 검증(StoreReceiptVerifierPort — 정품·유효 확인, 실패 시 예외).
 * ② 검증된 상품 id로 지급 코인 수를 결정한다.
 * ③ 코인을 적립(AcquireCoinUseCase)하고 적립 후 잔액을 반환한다.
 *
 * TODO(멱등): transactionId 기준 중복 지급 방지가 필요하다. 지금은 결제 기록 저장이 없어
 *   같은 거래를 재검증하면 재적립될 수 있다. IapPayment 기록(transactionId 유니크)을 도입해
 *   CompleteCoinPurchaseService의 paymentKey 멱등과 같은 방식으로 막아야 한다.
 */
@Service
class VerifyIapPurchaseService(
	private val storeReceiptVerifierPort: StoreReceiptVerifierPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
) : VerifyIapPurchaseUseCase {

	override fun verify(userId: Long, command: VerifyIapPurchaseCommand): VerifyIapPurchaseResult {
		// ① 스토어 영수증 검증(실패 시 예외).
		val verified: VerifiedReceipt = storeReceiptVerifierPort.verify(
			platform = command.platform,
			productId = command.productId,
			purchaseToken = command.purchaseToken,
			transactionId = command.transactionId,
		)

		// ② 상품 id → 지급 코인 수.
		val coinAmount: Int = coinAmountOf(verified.productId)

		// ③ 코인 적립(원장+잔액 정합) 후 잔액 반환.
		val balance: CoinBalance = acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(amount = coinAmount, coinType = CoinGetType.PURCHASE),
		)
		return VerifyIapPurchaseResult(coinBalance = balance.balance)
	}

	/**
	 * 스토어 상품 id(SKU)에서 지급 코인 수를 결정한다.
	 * 현재는 placeholder 규칙(com.oneulsogae.coins.<코인수>)의 접미 숫자를 쓴다.
	 * TODO: 스토어 SKU ↔ 코인 상품(CoinItem) 매핑 테이블로 대체한다.
	 */
	private fun coinAmountOf(productId: String): Int =
		productId.substringAfterLast('.').toIntOrNull()
			?: throw IllegalArgumentException("알 수 없는 코인 상품입니다: $productId")
}
