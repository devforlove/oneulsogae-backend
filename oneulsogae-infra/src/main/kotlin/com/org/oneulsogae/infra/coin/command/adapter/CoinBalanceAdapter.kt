package com.org.oneulsogae.infra.coin.command.adapter

import com.org.oneulsogae.core.coin.command.application.port.out.GetCoinBalancePort
import com.org.oneulsogae.core.coin.command.application.port.out.SaveCoinBalancePort
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.infra.coin.command.mapper.toDomain
import com.org.oneulsogae.infra.coin.command.mapper.toEntity
import com.org.oneulsogae.infra.coin.command.repository.CoinBalanceJpaRepository
import org.springframework.stereotype.Component

/**
 * 코인 잔액 아웃포트([GetCoinBalancePort], [SaveCoinBalancePort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class CoinBalanceAdapter(
	private val coinBalanceJpaRepository: CoinBalanceJpaRepository,
) : GetCoinBalancePort, SaveCoinBalancePort {

	override fun findByUserId(userId: Long): CoinBalance? =
		coinBalanceJpaRepository.findByUserId(userId)?.toDomain()

	override fun findByUserIdForUpdate(userId: Long): CoinBalance? =
		coinBalanceJpaRepository.findByUserIdForUpdate(userId)?.toDomain()

	override fun save(coinBalance: CoinBalance): CoinBalance =
		coinBalanceJpaRepository.save(coinBalance.toEntity()).toDomain()
}
