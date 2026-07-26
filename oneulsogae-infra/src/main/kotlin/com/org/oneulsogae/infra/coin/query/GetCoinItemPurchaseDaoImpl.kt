package com.org.oneulsogae.infra.coin.query

import com.org.oneulsogae.core.coin.query.dao.GetCoinItemPurchaseDao
import com.org.oneulsogae.infra.coin.command.repository.CoinItemPurchaseJpaRepository
import org.springframework.stereotype.Component

/** [GetCoinItemPurchaseDao]의 Spring Data 파생 쿼리 구현. */
@Component
class GetCoinItemPurchaseDaoImpl(
	private val coinItemPurchaseJpaRepository: CoinItemPurchaseJpaRepository,
) : GetCoinItemPurchaseDao {

	override fun exists(userId: Long, itemId: Long): Boolean =
		coinItemPurchaseJpaRepository.existsByUserIdAndItemId(userId, itemId)
}
