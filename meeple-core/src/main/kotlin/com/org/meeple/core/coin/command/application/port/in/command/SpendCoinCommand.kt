package com.org.meeple.core.coin.command.application.port.`in`.command

import com.org.meeple.common.coin.CoinUsageType

/** 코인 차감 명령. [coinUsageType]은 어떤 작업으로 차감하는지를 나타낸다. */
data class SpendCoinCommand(
	val amount: Int,
	val coinUsageType: CoinUsageType,
)
