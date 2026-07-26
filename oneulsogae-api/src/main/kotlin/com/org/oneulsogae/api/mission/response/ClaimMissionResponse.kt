package com.org.oneulsogae.api.mission.response

import com.org.oneulsogae.core.mission.command.application.port.`in`.result.ClaimMissionResult

/** 미션 보상 수령 응답 — 지급 코인과 적립 후 잔액. */
data class ClaimMissionResponse(
	val rewardedCoin: Int,
	val balance: Int,
) {
	companion object {

		fun of(result: ClaimMissionResult): ClaimMissionResponse =
			ClaimMissionResponse(rewardedCoin = result.rewardedCoin, balance = result.balance)
	}
}
