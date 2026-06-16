package com.org.meeple.infra.coin.command.adapter

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.core.coin.command.application.port.out.GetCoinPort
import com.org.meeple.core.coin.command.application.port.out.SaveCoinPort
import com.org.meeple.core.coin.command.domain.CoinHistory
import com.org.meeple.infra.coin.command.mapper.toDomain
import com.org.meeple.infra.coin.command.mapper.toEntity
import com.org.meeple.infra.coin.command.repository.CoinHistoryJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 코인 아웃포트([SaveCoinPort], [GetCoinPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환([CoinHistoryMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class CoinHistoryAdapter(
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
