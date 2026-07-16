package com.org.meeple.core.gathering.command.application.port.`in`.result

/** 참가 접수 결과. [amount]는 접수 시점 여분 기준으로 확정한 실결제가다. */
data class RegisterGatheringMemberResult(
	val memberId: Long,
	val amount: Int,
)
