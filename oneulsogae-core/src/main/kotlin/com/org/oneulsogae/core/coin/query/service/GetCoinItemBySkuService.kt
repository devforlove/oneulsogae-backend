package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.core.coin.CoinErrorCode
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinItemBySkuUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetCoinItemBySkuUseCase] 구현. SKU로 코인 상품을 조회하고 없으면 [CoinErrorCode.COIN_ITEM_NOT_FOUND]. */
@Service
@Transactional(readOnly = true)
class GetCoinItemBySkuService(
	private val getCoinItemDao: GetCoinItemDao,
) : GetCoinItemBySkuUseCase {

	override fun getBySku(storeProductId: String): CoinItem =
		getCoinItemDao.findByStoreProductId(storeProductId)
			?: throw BusinessException(CoinErrorCode.COIN_ITEM_NOT_FOUND)
}
