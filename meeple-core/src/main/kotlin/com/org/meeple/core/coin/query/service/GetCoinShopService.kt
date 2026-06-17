package com.org.meeple.core.coin.query.service

import com.org.meeple.core.coin.query.service.port.`in`.GetCoinShopUseCase
import com.org.meeple.core.coin.query.dao.GetCoinItemDao
import com.org.meeple.core.coin.query.dto.CoinItems
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetCoinShopUseCase] 구현.
 * 코인 상점에 필요한 코인 상품 목록을 아웃포트에서 조회해 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetCoinShopService(
	private val getCoinItemDao: GetCoinItemDao,
) : GetCoinShopUseCase {

	override fun getCoinShop(): CoinItems =
		getCoinItemDao.findAll()
}
