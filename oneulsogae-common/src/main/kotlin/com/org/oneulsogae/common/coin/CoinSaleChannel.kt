package com.org.oneulsogae.common.coin

/**
 * 코인 상품 판매 채널. PG(웹 결제)·IAP(앱 인앱결제)를 구분하며, BOTH는 두 채널에서 모두 판매한다.
 * 상점은 요청 채널로 노출 상품을 거르고, IAP 검증은 상품이 IAP 채널인지 확인한다.
 */
enum class CoinSaleChannel {
	PG,
	IAP,
	BOTH,
	;

	/** 이 상품이 [channel] 채널로 판매되는지 여부. BOTH는 어떤 채널이든 true. */
	fun sellableVia(channel: CoinSaleChannel): Boolean = this == BOTH || this == channel
}
