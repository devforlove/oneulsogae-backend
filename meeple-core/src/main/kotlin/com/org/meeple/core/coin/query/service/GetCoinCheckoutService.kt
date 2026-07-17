package com.org.meeple.core.coin.query.service

import com.org.meeple.core.coin.CoinErrorCode
import com.org.meeple.core.coin.query.dao.GetCoinItemDao
import com.org.meeple.core.coin.query.dto.CoinItem
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinCheckoutUseCase
import com.org.meeple.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetCoinCheckoutUseCase] 구현.
 * 코인 구매 체크아웃에 필요한 코인 아이템을 조회한다. 없으면 [CoinErrorCode.COIN_ITEM_NOT_FOUND].
 */
@Service
@Transactional(readOnly = true)
class GetCoinCheckoutService(
	private val getCoinItemDao: GetCoinItemDao,
) : GetCoinCheckoutUseCase {

	override fun getCheckout(itemId: Long): CoinItem =
		getCoinItemDao.findById(itemId)
			?: throw BusinessException(CoinErrorCode.COIN_ITEM_NOT_FOUND)
}
