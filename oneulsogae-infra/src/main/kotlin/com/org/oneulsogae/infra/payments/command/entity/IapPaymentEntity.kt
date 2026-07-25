package com.org.oneulsogae.infra.payments.command.entity

import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import com.org.oneulsogae.core.payments.command.domain.StorePlatform
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 인앱결제 코인 구매 기록 한 건. 스토어 거래 식별자(transaction_id)가 유니크라 같은 영수증 재검증을 멱등 처리한다.
 * (user_id) 인덱스로 사용자별 IAP 결제 내역 조회를 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "iap_payments",
	indexes = [
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class IapPaymentEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** SKU로 해석한 코인 상품 id(coin_items). */
	@Column(name = "item_id", nullable = false)
	val itemId: Long,

	@Enumerated(EnumType.STRING)
	@Column(name = "platform", nullable = false, columnDefinition = "varchar(10)")
	val platform: StorePlatform,

	/** 스토어 상품 id(SKU). */
	@Column(name = "product_id", nullable = false)
	val productId: String,

	/** 스토어 거래 식별자. 재검증 멱등을 위해 유니크. */
	@Column(name = "transaction_id", nullable = false, unique = true)
	val transactionId: String,

	/** 지급 코인 개수(스냅샷). */
	@Column(name = "coin_amount", nullable = false)
	val coinAmount: Int,

	/** 스토어 결제 상태. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	val status: PaymentStatus,
) : BaseEntity()
