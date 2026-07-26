package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.infra.coin.command.entity.CoinItemEntity

/**
 * [CoinItemEntity] 테스트 픽스처. 기본은 100코인·정가 12000·할인가 10000의 PG 전용 일반 상품이다.
 * 1회 패키지·IAP 상품은 인자로 지정한다.
 */
object CoinItemEntityFixture {

	fun create(
		coinAmount: Int = 100,
		price: Int = 12000,
		salePrice: Int = 10000,
		oncePerUser: Boolean = false,
		saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
		storeProductId: String? = null,
	): CoinItemEntity =
		CoinItemEntity(
			coinAmount = coinAmount,
			price = price,
			salePrice = salePrice,
			oncePerUser = oncePerUser,
			saleChannel = saleChannel,
			storeProductId = storeProductId,
		)
}
