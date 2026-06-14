package com.org.meeple.core.coin.application

import com.org.meeple.core.coin.application.port.`in`.GetCoinShopUseCase
import com.org.meeple.core.coin.application.port.out.GetCoinItemPort
import com.org.meeple.core.coin.domain.CoinItems
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetCoinShopUseCase] 구현.
 * 코인 상점에 필요한 코인 상품 목록을 아웃포트에서 조회해 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetCoinShopService(
	private val getCoinItemPort: GetCoinItemPort,
) : GetCoinShopUseCase {

	override fun getCoinShop(): CoinItems =
		getCoinItemPort.findAll()
}
