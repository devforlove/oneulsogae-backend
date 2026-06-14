package com.org.meeple.core.coin.application.port.out

import com.org.meeple.common.coin.CoinGetType
import java.time.LocalDateTime

/** 코인 적립 내역 조회 아웃포트. */
interface GetCoinPort {

	/**
	 * 사용자가 [from](포함)~[to](미포함) 구간에 [coinType]으로 적립한 내역이 있는지 여부를 반환한다.
	 * 하루 1회 등 기간 내 중복 적립을 방지하는 용도다.
	 */
	fun existsAcquired(
		userId: Long,
		coinType: CoinGetType,
		from: LocalDateTime,
		to: LocalDateTime,
	): Boolean
}
