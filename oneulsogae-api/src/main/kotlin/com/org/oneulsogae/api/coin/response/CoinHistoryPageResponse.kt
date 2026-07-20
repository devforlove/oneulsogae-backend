package com.org.oneulsogae.api.coin.response

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.core.coin.query.dto.CoinHistoryPage
import com.org.oneulsogae.core.coin.query.dto.CoinHistoryView
import java.time.LocalDateTime

/**
 * 코인 거래 내역 페이지 응답. (커서 페이징 — 최신순)
 * [content]는 현재 페이지의 거래 내역, [hasNext]는 다음(더 과거) 페이지 존재 여부,
 * [nextCursor]는 다음 페이지 요청에 넘길 커서(없으면 null)다.
 */
data class CoinHistoryPageResponse(
	val content: List<CoinHistoryItemResponse>,
	val hasNext: Boolean,
	val nextCursor: Long?,
) {
	companion object {
		fun of(page: CoinHistoryPage): CoinHistoryPageResponse =
			CoinHistoryPageResponse(
				content = page.values.map { history: CoinHistoryView -> CoinHistoryItemResponse.of(history) },
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
			)
	}
}

/**
 * 코인 거래 내역 한 건 응답.
 * 적립(획득/구매)은 양수 [amount]에 [coinGetType]이, 차감(사용)은 음수 [amount]에 [coinUsageType]이 채워진다.
 */
data class CoinHistoryItemResponse(
	val id: Long,
	/** 거래 수량. 적립은 양수, 차감(사용)은 음수. */
	val amount: Int,
	/** 적립(획득) 유형. 차감 내역이면 null. */
	val coinGetType: CoinGetType?,
	/** 차감(사용) 유형. 적립 내역이면 null. */
	val coinUsageType: CoinUsageType?,
	/** 거래가 발생한 시각. */
	val occurredAt: LocalDateTime,
) {
	companion object {
		fun of(history: CoinHistoryView): CoinHistoryItemResponse =
			CoinHistoryItemResponse(
				id = history.id,
				amount = history.amount,
				coinGetType = history.coinGetType,
				coinUsageType = history.coinUsageType,
				occurredAt = history.occurredAt,
			)
	}
}
