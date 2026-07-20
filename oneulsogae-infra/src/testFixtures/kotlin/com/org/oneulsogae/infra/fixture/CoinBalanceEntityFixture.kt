package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.coin.command.entity.CoinBalanceEntity

/**
 * [CoinBalanceEntity] 테스트 픽스처. 기본 잔액은 차감 시나리오를 넉넉히 커버하도록 100으로 둔다.
 */
object CoinBalanceEntityFixture {

	fun create(
		userId: Long = 1L,
		balance: Int = 100,
	): CoinBalanceEntity =
		CoinBalanceEntity(
			userId = userId,
			balance = balance,
		)
}
