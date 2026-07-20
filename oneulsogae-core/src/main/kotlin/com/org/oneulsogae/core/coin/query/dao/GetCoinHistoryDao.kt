package com.org.oneulsogae.core.coin.query.dao

import com.org.oneulsogae.core.coin.query.dto.CoinHistoryView

/**
 * 코인 거래 내역 조회 dao. (조회 전용 — 사용/획득 원장을 모두 읽는다)
 */
interface GetCoinHistoryDao {

	/**
	 * 사용자의 코인 거래 내역을 최신(id 내림차순)순으로 최대 [limit]건 조회한다.
	 * [beforeId]를 주면 그보다 과거(id 미만) 구간을 잇는다. (커서 페이징)
	 */
	fun findPageByUserId(userId: Long, beforeId: Long?, limit: Int): List<CoinHistoryView>
}
