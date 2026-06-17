package com.org.meeple.infra.coin.query

import com.org.meeple.core.coin.query.dao.CoinItemDao
import com.org.meeple.core.coin.query.dto.CoinItem
import com.org.meeple.core.coin.query.dto.CoinItems
import com.org.meeple.infra.coin.command.entity.QCoinItemEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 코인 상품 조회 dao([CoinItemDao])의 QueryDSL 구현.
 * 등록된 전체 코인 상품을 [CoinItems] read model로 직접 투영한다.
 */
@Component
class CoinItemDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : CoinItemDao {

	override fun findAll(): CoinItems {
		val item: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		return CoinItems(
			queryFactory
				.select(
					Projections.constructor(
						CoinItem::class.java,
						item.id,
						item.coinAmount,
						item.price,
						item.salePrice,
					),
				)
				.from(item)
				.fetch(),
		)
	}
}
