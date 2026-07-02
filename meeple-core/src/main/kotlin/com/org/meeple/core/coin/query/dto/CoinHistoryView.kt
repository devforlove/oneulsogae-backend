package com.org.meeple.core.coin.query.dto

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.common.coin.CoinUsageType
import java.time.LocalDateTime

/**
 * 코인 거래 내역 한 건(read model). 적립(획득/구매)과 차감(사용)이 모두 담긴다.
 * 적립은 양수 [amount]에 [coinGetType]이, 차감은 음수 [amount]에 [coinUsageType]이 채워진다.
 */
data class CoinHistoryView(
	val id: Long,
	/** 거래 수량. 적립은 양수, 차감(사용)은 음수. */
	val amount: Int,
	/** 적립(획득) 유형. 차감 내역이면 null. */
	val coinGetType: CoinGetType?,
	/** 차감(사용) 유형. 적립 내역이면 null. */
	val coinUsageType: CoinUsageType?,
	/** 거래가 발생한 시각. */
	val occurredAt: LocalDateTime,
)
