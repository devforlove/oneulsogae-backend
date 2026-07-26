package com.org.oneulsogae.infra.coin.command.entity

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * coin_items 테이블 영속성 엔티티. 판매(구매) 가능한 코인 상품을 정의한다.
 * 각 상품은 지급 코인 개수·정가([price])·할인가([salePrice])와 함께,
 * 회원당 1회 제한([oncePerUser])·판매 채널([saleChannel])·스토어 SKU([storeProductId])를 가진다.
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

	/** 회원당 1회만 구매 가능한 패키지 여부. */
	@Column(name = "once_per_user", nullable = false)
	var oncePerUser: Boolean = false,

	/** 판매 채널(PG·IAP·BOTH). */
	@Enumerated(EnumType.STRING)
	@Column(name = "sale_channel", nullable = false, columnDefinition = "varchar(10)")
	var saleChannel: CoinSaleChannel = CoinSaleChannel.PG,

	/** 스토어 상품 id(SKU). IAP 검증이 SKU→coin_item 해석에 쓴다. PG 전용 상품은 null. 유니크. */
	@Column(name = "store_product_id", unique = true)
	var storeProductId: String? = null,
) : BaseEntity()
