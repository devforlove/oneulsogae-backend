package com.org.meeple.infra.coin.adapter

import com.org.meeple.core.coin.application.port.out.GetCoinItemPort
import com.org.meeple.core.coin.domain.CoinItems
import com.org.meeple.infra.coin.mapper.toDomain
import com.org.meeple.infra.coin.repository.CoinItemJpaRepository
import org.springframework.stereotype.Component

/**
 * 코인 상품 아웃포트([GetCoinItemPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환([CoinItemMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class CoinItemCoreAdapter(
	private val coinItemJpaRepository: CoinItemJpaRepository,
) : GetCoinItemPort {

	override fun findAll(): CoinItems =
		CoinItems(coinItemJpaRepository.findAll().map { it.toDomain() })
}
