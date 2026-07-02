package com.org.meeple.core.coin.query.dto

/**
 * 코인 거래 내역([CoinHistoryView])의 커서 페이지(일급 컬렉션).
 * 최신(id 내림차순)순 목록과 다음 페이지 존재 여부·커서를 함께 담아, 커서 산출 규칙을 한곳에 응집시킨다.
 */
class CoinHistoryPage private constructor(
	/** 현재 페이지의 거래 내역. 최신(id 내림차순)순. */
	val values: List<CoinHistoryView>,
	/** 다음(더 과거) 페이지가 있는지 여부. */
	val hasNext: Boolean,
) {

	/** 다음(더 과거) 페이지 조회의 기준 커서. 현재 페이지 마지막(가장 오래된) 내역의 id이며, 다음 페이지가 없으면 null. */
	val nextCursor: Long?
		get() = if (hasNext) values.lastOrNull()?.id else null

	companion object {

		/**
		 * "한 건 더 읽기(size + 1)"로 조회한 행들로 페이지를 만든다.
		 * [rows]가 [size]보다 많으면 다음 페이지가 있는 것으로 보고, 초과분은 잘라낸다.
		 */
		fun of(rows: List<CoinHistoryView>, size: Int): CoinHistoryPage =
			CoinHistoryPage(values = rows.take(size), hasNext = rows.size > size)
	}
}
