package com.org.oneulsogae.infra.coin.query

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 코인 상품 조회 dao([GetCoinItemDao])의 QueryDSL 구현.
 * 상점 목록은 요청 채널(+BOTH) 상품을 [CoinItem] read model로 투영하되, 이미 산 1회 패키지는 left join으로 걸러낸다.
 */
@Component
class GetCoinItemDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCoinItemDao {

	override fun findShopItems(userId: Long, channel: CoinSaleChannel): CoinItems {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		val purchase: QCoinItemPurchaseEntity = QCoinItemPurchaseEntity.coinItemPurchaseEntity
		return CoinItems(
			queryFactory
				.select(projection(coinItem))
				.from(coinItem)
				// 이 사용자의 이 상품 구매 가드를 붙여, once_per_user + 구매분을 where에서 제외한다.
				.leftJoin(purchase)
				.on(purchase.itemId.eq(coinItem.id).and(purchase.userId.eq(userId)))
				.where(
					// 요청 채널 또는 BOTH.
					coinItem.saleChannel.eq(channel).or(coinItem.saleChannel.eq(CoinSaleChannel.BOTH)),
					// once_per_user이고 이미 구매(purchase 매칭)면 제외.
					coinItem.oncePerUser.isFalse.or(purchase.id.isNull),
				)
				.fetch(),
		)
	}

	override fun findById(itemId: Long): CoinItem? {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		return queryFactory
			.select(projection(coinItem))
			.from(coinItem)
			.where(coinItem.id.eq(itemId))
			.fetchOne()
	}

	override fun findByStoreProductId(storeProductId: String): CoinItem? {
		val coinItem: QCoinItemEntity = QCoinItemEntity.coinItemEntity
		return queryFactory
			.select(projection(coinItem))
			.from(coinItem)
			.where(coinItem.storeProductId.eq(storeProductId))
			.fetchOne()
	}

	/** 6-arg CoinItem 생성자 투영. (id, coinAmount, price, salePrice, oncePerUser, saleChannel, storeProductId) */
	private fun projection(coinItem: QCoinItemEntity) =
		Projections.constructor(
			CoinItem::class.java,
			coinItem.id,
			coinItem.coinAmount,
			coinItem.price,
			coinItem.salePrice,
			coinItem.oncePerUser,
			coinItem.saleChannel,
			coinItem.storeProductId,
		)
}
