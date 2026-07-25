package com.org.oneulsogae.core.coin.command.application.port.`in`

import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.coin.query.dto.CoinItem

/**
 * 코인 상품 구매 적립 인포트. 코인 적립과 (1회 패키지면) 구매 가드 기록을 **한 트랜잭션**에서 처리한다.
 * 가드 유니크 위반(경합 이중구매)은 적립까지 롤백돼 이중적립이 원천 차단된다.
 * PG·IAP 결제 경로가 코인 구매 적립에 이 인포트를 공유한다.
 */
interface AcquirePurchasedCoinUseCase {

	/** [item]을 [userId]에게 적립하고 갱신된 잔액을 반환한다. [item.oncePerUser]면 구매 가드도 함께 기록한다. */
	fun acquire(userId: Long, item: CoinItem): CoinBalance
}
