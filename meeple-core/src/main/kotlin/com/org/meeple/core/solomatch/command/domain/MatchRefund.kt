package com.org.meeple.core.solomatch.command.domain

/**
 * 매칭 실패 시 한 참가자에게 돌려줄 환불 정보.
 * [userId]에게 [amount]코인을 환불한다. (실제 적립은 coin 도메인 인포트가 수행한다)
 */
data class MatchRefund(
	val userId: Long,
	val amount: Int,
)
