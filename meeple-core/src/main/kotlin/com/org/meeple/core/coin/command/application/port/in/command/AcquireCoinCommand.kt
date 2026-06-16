package com.org.meeple.core.coin.command.application.port.`in`.command

import com.org.meeple.common.coin.CoinGetType

/** 코인 획득 명령. */
data class AcquireCoinCommand(
	val amount: Int,
	val coinType: CoinGetType,
)
