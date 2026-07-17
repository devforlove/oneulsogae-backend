package com.org.meeple.infra.coin.query

import com.org.meeple.core.coin.query.dao.GetCoinItemDao
import com.org.meeple.core.coin.query.dto.CoinItem
import com.org.meeple.core.coin.query.dto.CoinItems
import com.org.meeple.infra.coin.command.entity.QCoinItemEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 코인 상품 조회 dao([GetCoinItemDao])의 QueryDSL 구현.
 * 등록된 전체 코인 상품을 [CoinItems] read model로 직접 투영한다.
 */
@Component
class GetCoinItemDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCoinItemDao {

	override fun findAll(): CoinItems {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		return CoinItems(
			queryFactory
				.select(
					Projections.constructor(
						CoinItem::class.java,
						coinItem.id,
						coinItem.coinAmount,
						coinItem.price,
						coinItem.salePrice,
					),
				)
				.from(coinItem)
				.fetch(),
		)
	}

	override fun findById(itemId: Long): CoinItem? {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		return queryFactory
			.select(
				Projections.constructor(
					CoinItem::class.java,
					coinItem.id,
					coinItem.coinAmount,
					coinItem.price,
					coinItem.salePrice,
				),
			)
			.from(coinItem)
			.where(coinItem.id.eq(itemId))
			.fetchOne()
	}
}
