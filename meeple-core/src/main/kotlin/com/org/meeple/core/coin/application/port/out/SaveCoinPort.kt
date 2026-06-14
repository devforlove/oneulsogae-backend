package com.org.meeple.core.coin.application.port.out

import com.org.meeple.core.coin.domain.CoinHistory

/**
 * 코인 저장 아웃포트.
 * 신규 적립 내역을 저장하거나, 기존 내역(id 존재)의 변경분을 반영한다.
 */
interface SaveCoinPort {

	fun save(coin: CoinHistory): CoinHistory
}
