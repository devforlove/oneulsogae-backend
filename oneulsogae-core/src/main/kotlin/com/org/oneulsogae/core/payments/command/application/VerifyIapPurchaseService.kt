package com.org.oneulsogae.core.payments.command.application

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquirePurchasedCoinUseCase
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinItemBySkuUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.IsCoinItemPurchasedUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import com.org.oneulsogae.core.payments.command.application.port.`in`.VerifyIapPurchaseUseCase
import com.org.oneulsogae.core.payments.command.application.port.`in`.command.VerifyIapPurchaseCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.VerifyIapPurchaseResult
import com.org.oneulsogae.core.payments.command.application.port.out.GetIapPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.SaveIapPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.StoreReceiptVerifierPort
import com.org.oneulsogae.core.payments.command.application.port.out.VerifiedReceipt
import com.org.oneulsogae.core.payments.command.domain.IapPayment
import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import org.springframework.stereotype.Service

/**
 * [VerifyIapPurchaseUseCase] 구현. 스토어 인앱결제 영수증을 검증하고 코인을 적립한다.
 * ① SKU→coin_item 해석([GetCoinItemBySkuUseCase]) — IAP 채널 상품인지도 확인.
 * ② transaction_id 멱등([GetIapPaymentPort]) — 이미 처리한 영수증이면 현재 잔액으로 재생(재적립 없음).
 * ③ 1회 패키지 선검사([IsCoinItemPurchasedUseCase]) — 이미 샀으면 409.
 * ④ 영수증 검증([StoreReceiptVerifierPort]) → 적립+가드 원자([AcquirePurchasedCoinUseCase]) → iap_payments 기록.
 */
@Service
class VerifyIapPurchaseService(
	private val getCoinItemBySkuUseCase: GetCoinItemBySkuUseCase,
	private val getIapPaymentPort: GetIapPaymentPort,
	private val isCoinItemPurchasedUseCase: IsCoinItemPurchasedUseCase,
	private val storeReceiptVerifierPort: StoreReceiptVerifierPort,
	private val acquirePurchasedCoinUseCase: AcquirePurchasedCoinUseCase,
	private val saveIapPaymentPort: SaveIapPaymentPort,
	private val getCoinBalanceUseCase: GetCoinBalanceUseCase,
) : VerifyIapPurchaseUseCase {

	override fun verify(userId: Long, command: VerifyIapPurchaseCommand): VerifyIapPurchaseResult {
		// ① SKU → coin_item. IAP로 팔리는 상품이 아니면 거부.
		val item: CoinItem = getCoinItemBySkuUseCase.getBySku(command.productId)
		if (!item.saleChannel.sellableVia(CoinSaleChannel.IAP)) {
			throw BusinessException(PaymentsErrorCode.COIN_ITEM_NOT_SOLD_VIA_IAP)
		}

		// ② 멱등: 같은 거래를 이미 처리했으면 재적립 없이 현재 잔액으로 재생한다.
		if (getIapPaymentPort.findByTransactionId(command.transactionId) != null) {
			return VerifyIapPurchaseResult(coinBalance = getCoinBalanceUseCase.getBalance(userId).balance)
		}

		// ③ 1회 패키지 선검사.
		if (item.oncePerUser && isCoinItemPurchasedUseCase.isPurchased(userId, item.id)) {
			throw BusinessException(PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED)
		}

		// ④ 영수증 검증(실패 시 예외).
		val verified: VerifiedReceipt = storeReceiptVerifierPort.verify(
			platform = command.platform,
			productId = command.productId,
			purchaseToken = command.purchaseToken,
			transactionId = command.transactionId,
		)

		// 적립+가드 원자.
		val balance: CoinBalance = acquirePurchasedCoinUseCase.acquire(userId, item)

		// IAP 기록(transaction_id 유니크). 경합으로 이미 있으면 재생 경로와 같은 의미이므로 현재 잔액으로 응답한다.
		saveIapPaymentPort.save(
			IapPayment(
				userId = userId,
				itemId = item.id,
				platform = command.platform,
				productId = verified.productId,
				transactionId = verified.transactionId,
				coinAmount = item.coinAmount,
				status = PaymentStatus.APPROVED,
			),
		)

		return VerifyIapPurchaseResult(coinBalance = balance.balance)
	}
}
