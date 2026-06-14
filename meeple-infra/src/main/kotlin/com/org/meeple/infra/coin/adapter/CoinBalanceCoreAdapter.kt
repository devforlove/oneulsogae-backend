package com.org.meeple.infra.coin.adapter

import com.org.meeple.core.coin.application.port.out.GetCoinBalancePort
import com.org.meeple.core.coin.application.port.out.SaveCoinBalancePort
import com.org.meeple.core.coin.domain.CoinBalance
import com.org.meeple.infra.coin.mapper.toDomain
import com.org.meeple.infra.coin.mapper.toEntity
import com.org.meeple.infra.coin.repository.CoinBalanceJpaRepository
import org.springframework.stereotype.Component

/**
 * 코인 잔액 아웃포트([GetCoinBalancePort], [SaveCoinBalancePort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class CoinBalanceCoreAdapter(
	private val coinBalanceJpaRepository: CoinBalanceJpaRepository,
) : GetCoinBalancePort, SaveCoinBalancePort {

	override fun findByUserId(userId: Long): CoinBalance? =
		coinBalanceJpaRepository.findByUserId(userId)?.toDomain()

	override fun findByUserIdForUpdate(userId: Long): CoinBalance? =
		coinBalanceJpaRepository.findByUserIdForUpdate(userId)?.toDomain()

	override fun save(coinBalance: CoinBalance): CoinBalance =
		coinBalanceJpaRepository.save(coinBalance.toEntity()).toDomain()
}
