package com.org.oneulsogae.core.coin.command.application.port.`in`

/** 코인 잔액 행 생성 인포트. (적립/차감 전에 잔액 행을 준비하는 커맨드) */
interface CreateCoinBalanceUseCase {

	/** 사용자의 코인 잔액 행이 없으면 0 잔액으로 생성한다. 이미 있으면 아무것도 하지 않는다. (멱등) */
	fun createIfAbsent(userId: Long)
}
