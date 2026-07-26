package com.org.oneulsogae.core.coin.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquirePurchasedCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.coin.command.application.port.out.SaveCoinItemPurchasePort
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcquirePurchasedCoinUseCase] 구현.
 * 코인 적립([AcquireCoinUseCase])과 1회 패키지 구매 가드 기록([SaveCoinItemPurchasePort])을 **한 트랜잭션**에서 처리한다.
 * 가드 (user_id, item_id) 유니크 위반이면 트랜잭션이 롤백돼 적립도 취소되고 409([PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED])로 매핑한다.
 * (이 원자성이 PG·IAP 경로의 경합 이중적립을 원천 차단한다)
 */
@Service
class AcquirePurchasedCoinService(
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val saveCoinItemPurchasePort: SaveCoinItemPurchasePort,
) : AcquirePurchasedCoinUseCase {

	@Transactional
	override fun acquire(userId: Long, item: CoinItem): CoinBalance {
		val balance: CoinBalance = acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(amount = item.coinAmount, coinType = CoinGetType.PURCHASE),
		)
		if (item.oncePerUser) {
			try {
				saveCoinItemPurchasePort.save(userId, item.id)
			} catch (_: DataIntegrityViolationException) {
				// 선검사와 적립 사이 경합으로 가드가 먼저 들어간 경우. 트랜잭션 롤백 → 적립 취소.
				throw BusinessException(PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED)
			}
		}
		return balance
	}
}
