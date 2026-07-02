package com.org.meeple.core.coin.query.service

import com.org.meeple.core.coin.query.dao.GetCoinHistoryDao
import com.org.meeple.core.coin.query.dto.CoinHistoryPage
import com.org.meeple.core.coin.query.dto.CoinHistoryView
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinHistoriesUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetCoinHistoriesUseCase] 구현. 조회 dao([GetCoinHistoryDao])에만 의존한다.
 * 페이지 크기 + 1건을 읽어 다음 페이지 존재 여부를 판정한다. (COUNT 없이 커서 페이징)
 */
@Service
class GetCoinHistoriesService(
	private val getCoinHistoryDao: GetCoinHistoryDao,
) : GetCoinHistoriesUseCase {

	@Transactional(readOnly = true)
	override fun getHistories(userId: Long, cursor: Long?): CoinHistoryPage {
		val rows: List<CoinHistoryView> = getCoinHistoryDao.findPageByUserId(userId, cursor, PAGE_SIZE + 1)
		return CoinHistoryPage.of(rows, PAGE_SIZE)
	}

	companion object {
		/** 한 페이지에 내려주는 거래 내역 건수. */
		const val PAGE_SIZE: Int = 50
	}
}
