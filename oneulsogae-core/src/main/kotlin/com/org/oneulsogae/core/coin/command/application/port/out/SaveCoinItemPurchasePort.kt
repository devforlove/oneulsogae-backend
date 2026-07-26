package com.org.oneulsogae.core.coin.command.application.port.out

/**
 * 회원당 1회 구매 코인 패키지의 구매 가드 기록 저장 out-port.
 * (user_id, item_id) 유니크라 이미 있으면 저장 시 DataIntegrityViolationException이 발생한다 —
 * 이 위반이 경로 무관 이중구매를 막는 최종 방어선이다.
 */
interface SaveCoinItemPurchasePort {

	fun save(userId: Long, itemId: Long)
}
