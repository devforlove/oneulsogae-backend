package com.org.oneulsogae.infra.coin.command.repository

import com.org.oneulsogae.infra.coin.command.entity.CoinItemPurchaseEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 코인 패키지 구매 가드 리포지토리. 도메인 포트는 어댑터가 구현한다. */
interface CoinItemPurchaseJpaRepository : JpaRepository<CoinItemPurchaseEntity, Long> {

	fun existsByUserIdAndItemId(userId: Long, itemId: Long): Boolean
}
