package com.org.meeple.core.coin.query.service.port.`in`

import com.org.meeple.core.coin.query.dto.CoinHistoryPage

/**
 * 코인 거래 내역(사용/획득 전체) 조회 유스케이스.
 * 내역이 길 수 있어 커서 기반으로 한 페이지씩 내려준다.
 */
interface GetCoinHistoriesUseCase {

	/** 사용자의 거래 내역 한 페이지를 최신순으로 조회한다. [cursor]를 주면 그보다 과거 구간을 잇는다. */
	fun getHistories(userId: Long, cursor: Long?): CoinHistoryPage
}
