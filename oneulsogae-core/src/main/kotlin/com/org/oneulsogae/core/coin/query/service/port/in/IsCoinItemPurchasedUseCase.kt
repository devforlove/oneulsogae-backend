package com.org.oneulsogae.core.coin.query.service.port.`in`

/** 1회 패키지 구매 선검사 인포트. 결제 서비스가 승인·검증 전에 이미 구매를 걸러 이른 409를 낸다. */
interface IsCoinItemPurchasedUseCase {

	fun isPurchased(userId: Long, itemId: Long): Boolean
}
