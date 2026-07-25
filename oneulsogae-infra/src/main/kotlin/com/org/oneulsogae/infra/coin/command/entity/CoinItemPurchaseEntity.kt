package com.org.oneulsogae.infra.coin.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 회원당 1회 구매 코인 패키지의 구매 가드 기록. once_per_user 상품을 실제 적립 성공한 시점에 저장한다.
 * (user_id, item_id) 유니크가 경로(PG·IAP) 무관 이중구매를 원자적으로 막는다.
 */
@Entity
@Table(
	name = "coin_item_purchases",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_coin_item_purchases_user_item", columnNames = ["user_id", "item_id"]),
	],
)
class CoinItemPurchaseEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "item_id", nullable = false)
	val itemId: Long,
) : BaseEntity()
