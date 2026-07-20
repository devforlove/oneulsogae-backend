package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.infra.coin.command.entity.CoinHistoryEntity
import java.time.LocalDateTime

/**
 * [CoinHistoryEntity](코인 거래 원장) 테스트 픽스처.
 * 기본은 무료 획득(DAILY) 10코인 적립이며, 차감 내역은 음수 amount + [coinUsageType]으로 지정한다.
 */
object CoinHistoryEntityFixture {

	fun create(
		userId: Long = 1L,
		amount: Int = 10,
		coinGetType: CoinGetType? = CoinGetType.DAILY,
		coinUsageType: CoinUsageType? = null,
		occurredAt: LocalDateTime = LocalDateTime.now(),
	): CoinHistoryEntity =
		CoinHistoryEntity(
			userId = userId,
			amount = amount,
			coinGetType = coinGetType,
			coinUsageType = coinUsageType,
			occurredAt = occurredAt,
		)
}
