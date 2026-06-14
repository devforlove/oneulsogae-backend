package com.org.meeple.infra.coin.adapter

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.core.coin.application.port.out.GetCoinPort
import com.org.meeple.core.coin.application.port.out.SaveCoinPort
import com.org.meeple.core.coin.domain.CoinHistory
import com.org.meeple.infra.coin.mapper.toDomain
import com.org.meeple.infra.coin.mapper.toEntity
import com.org.meeple.infra.coin.repository.CoinHistoryJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 코인 아웃포트([SaveCoinPort], [GetCoinPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환([CoinHistoryMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class CoinHistoryCoreAdapter(
	private val coinHistoryJpaRepository: CoinHistoryJpaRepository,
) : SaveCoinPort, GetCoinPort {

	override fun save(coin: CoinHistory): CoinHistory =
		coinHistoryJpaRepository.save(coin.toEntity()).toDomain()

	override fun existsAcquired(
		userId: Long,
		coinType: CoinGetType,
		from: LocalDateTime,
		to: LocalDateTime,
	): Boolean =
		coinHistoryJpaRepository.existsByUserIdAndCoinGetTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
			userId,
			coinType,
			from,
			to,
		)
}
