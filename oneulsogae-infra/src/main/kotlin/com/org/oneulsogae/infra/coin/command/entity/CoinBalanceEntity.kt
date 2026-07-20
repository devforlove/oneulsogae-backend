package com.org.oneulsogae.infra.coin.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * coin_balances 테이블 영속성 엔티티. 사용자별 코인 잔액(총합)을 물질화해 보관한다. (user당 1행)
 * coins 적립 원장의 합을 캐시한 값이며, 적립/차감과 동일 트랜잭션에서 갱신된다.
 * 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "coin_balances",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_user_id", columnNames = ["user_id"]),
	],
)
class CoinBalanceEntity(
	/** 잔액 소유 사용자 id. (user당 1행) */
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 현재 코인 잔액(총합). */
	@Column(name = "balance", nullable = false)
	var balance: Int,
) : BaseEntity()
