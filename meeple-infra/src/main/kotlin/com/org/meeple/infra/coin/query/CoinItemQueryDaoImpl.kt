package com.org.meeple.infra.coin.query

import com.org.meeple.core.coin.query.dao.CoinItemQueryDao
import com.org.meeple.core.coin.query.dto.CoinItem
import com.org.meeple.core.coin.query.dto.CoinItems
import com.org.meeple.infra.coin.command.entity.CoinItemEntity
import com.org.meeple.infra.coin.command.repository.CoinItemJpaRepository
import org.springframework.stereotype.Component

/**
 * 코인 상품 조회 dao([CoinItemQueryDao]) 구현.
 * 등록된 전체 코인 상품을 [CoinItems] read model로 직접 투영한다. (매퍼에 의존하지 않고 엔티티 필드에서 구성)
 * entity·repository는 command 아래에 있고, infra 내부 query→command 참조는 허용된다.
 */
@Component
class CoinItemQueryDaoImpl(
	private val coinItemJpaRepository: CoinItemJpaRepository,
) : CoinItemQueryDao {

	override fun findAll(): CoinItems =
		CoinItems(
			coinItemJpaRepository.findAll().map { entity: CoinItemEntity ->
				CoinItem(
					id = entity.id ?: 0,
					coinAmount = entity.coinAmount,
					price = entity.price,
					salePrice = entity.salePrice,
				)
			},
		)
}
