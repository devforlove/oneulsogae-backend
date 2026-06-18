package com.org.meeple.core.coin

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 코인 도메인 에러 코드. */
enum class CoinErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	INSUFFICIENT_COIN_BALANCE("COIN-001", "코인 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
	DAILY_COIN_ALREADY_ACQUIRED("COIN-003", "오늘은 이미 출석 코인을 받았습니다.", HttpStatus.CONFLICT),
}
