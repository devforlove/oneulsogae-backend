package com.org.meeple.infra.coin.command.repository

import com.org.meeple.infra.coin.command.entity.CoinItemEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 코인 상품 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 infra 레이어의 어댑터가 구현한다.
 */
interface CoinItemJpaRepository : JpaRepository<CoinItemEntity, Long>
