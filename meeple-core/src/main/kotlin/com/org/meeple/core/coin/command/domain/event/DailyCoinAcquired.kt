package com.org.meeple.core.coin.command.domain.event

/**
 * 출석(DAILY) 코인이 적립됐을 때 발행되는 도메인 이벤트.
 * 수신측(알람 저장 등)이 후속 처리에 필요한 정보만 담는다.
 * [userId]는 적립 대상(알람 수신자), [amount]는 적립된 코인 수량이다.
 */
data class DailyCoinAcquired(
	val userId: Long,
	val amount: Int,
)
