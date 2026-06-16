package com.org.meeple.api.coin.request

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/** 코인 획득 요청. 사용자 식별은 로그인 정보에서 가져오므로 받지 않는다. */
data class AcquireCoinRequest(
	@field:NotNull(message = "수량은 필수입니다.")
	@field:Positive(message = "수량은 1 이상이어야 합니다.")
	val amount: Int?,

	@field:NotNull(message = "코인 타입은 필수입니다.")
	val coinType: CoinGetType?,
) {

	fun toCommand(): AcquireCoinCommand =
		AcquireCoinCommand(
			amount = amount!!,
			coinType = coinType!!,
		)
}
