package com.org.oneulsogae.core.mission.command.application.port.`in`.result

/** 미션 보상 수령 결과 — 지급 코인과 적립 후 잔액. */
data class ClaimMissionResult(
	val rewardedCoin: Int,
	val balance: Int,
)
