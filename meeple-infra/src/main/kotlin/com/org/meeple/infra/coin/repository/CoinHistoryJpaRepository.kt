package com.org.meeple.infra.coin.repository

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.infra.coin.entity.CoinHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

/**
 * 코인 적립 내역 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 infra 레이어의 어댑터가 구현한다.
 */
interface CoinHistoryJpaRepository : JpaRepository<CoinHistoryEntity, Long> {

	/** 특정 사용자의 모든 코인 적립 내역을 조회한다. */
	fun findByUserId(userId: Long): List<CoinHistoryEntity>

	/** 사용자가 [from](포함)~[to](미포함) 구간에 해당 타입으로 적립한 내역이 있는지 여부. */
	fun existsByUserIdAndCoinGetTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
		userId: Long,
		coinGetType: CoinGetType,
		from: LocalDateTime,
		to: LocalDateTime,
	): Boolean
}
