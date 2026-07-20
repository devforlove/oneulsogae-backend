package com.org.oneulsogae.infra.coin.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * coin_items 테이블 영속성 엔티티. 판매(구매) 가능한 코인 상품을 정의한다.
 * 각 상품은 지급되는 코인 개수와 정가([price]), 할인가([salePrice])를 가진다.
 * 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "coin_items")
class CoinItemEntity(
	/** 상품 구매 시 지급되는 코인 개수. */
	@Column(name = "coin_amount", nullable = false)
	var coinAmount: Int,

	/** 정가. */
	@Column(name = "price", nullable = false)
	var price: Int,

	/** 할인가. (실제 결제 가격) */
	@Column(name = "sale_price", nullable = false)
	var salePrice: Int,
) : BaseEntity()
