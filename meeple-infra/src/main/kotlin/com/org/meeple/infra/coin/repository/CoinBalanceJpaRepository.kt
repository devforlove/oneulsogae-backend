package com.org.meeple.infra.coin.repository

import com.org.meeple.infra.coin.entity.CoinBalanceEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 코인 잔액 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 infra 레이어의 어댑터가 구현한다.
 */
interface CoinBalanceJpaRepository : JpaRepository<CoinBalanceEntity, Long> {

	/** 사용자의 코인 잔액을 조회한다. */
	fun findByUserId(userId: Long): CoinBalanceEntity?

	/**
	 * 차감/적립 갱신을 위해 비관적 쓰기 락으로 잔액 행을 조회한다. (SELECT ... FOR UPDATE)
	 * 트랜잭션 커밋 전까지 행을 잠가 동시 차감을 직렬화한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from CoinBalanceEntity b where b.userId = :userId")
	fun findByUserIdForUpdate(@Param("userId") userId: Long): CoinBalanceEntity?
}
