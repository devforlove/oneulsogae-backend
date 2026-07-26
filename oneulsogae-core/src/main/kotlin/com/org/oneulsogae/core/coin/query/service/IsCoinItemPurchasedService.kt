package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.core.coin.query.dao.GetCoinItemPurchaseDao
import com.org.oneulsogae.core.coin.query.service.port.`in`.IsCoinItemPurchasedUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [IsCoinItemPurchasedUseCase] 구현. 구매 가드 존재 여부를 조회한다. */
@Service
@Transactional(readOnly = true)
class IsCoinItemPurchasedService(
	private val getCoinItemPurchaseDao: GetCoinItemPurchaseDao,
) : IsCoinItemPurchasedUseCase {

	override fun isPurchased(userId: Long, itemId: Long): Boolean =
		getCoinItemPurchaseDao.exists(userId, itemId)
}
