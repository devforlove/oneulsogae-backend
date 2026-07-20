package com.org.oneulsogae.api.coin.request

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/** 코인 차감(사용) 요청. 사용자 식별은 로그인 정보에서 가져오므로 받지 않는다. */
data class SpendCoinRequest(
	@field:NotNull(message = "수량은 필수입니다.")
	@field:Positive(message = "수량은 1 이상이어야 합니다.")
	val amount: Int?,

	@field:NotNull(message = "사용 유형은 필수입니다.")
	val coinUsageType: CoinUsageType?,
) {

	fun toCommand(): SpendCoinCommand =
		SpendCoinCommand(
			amount = amount!!,
			coinUsageType = coinUsageType!!,
		)
}
