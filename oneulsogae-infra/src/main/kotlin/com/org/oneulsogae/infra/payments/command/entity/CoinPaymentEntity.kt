package com.org.oneulsogae.infra.payments.command.entity

import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 코인 구매 결제 기록 한 건. 접수 시점에 PENDING으로 저장하고 PG 승인 결과로 APPROVED/FAILED로 전이한다.
 * 누가(user_id)·어떤 코인 상품을(item_id)·몇 코인을(coin_amount)·얼마에(amount) 구매했는지 보관한다.
 * (user_id) 인덱스로 사용자별 코인 결제 내역 조회를 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "coin_payments",
	indexes = [
		// 사용자별 코인 결제 기록 조회.
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class CoinPaymentEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 구매한 코인 상품 id(coin_items). 가격·지급 코인 개수의 근거. */
	@Column(name = "item_id", nullable = false)
	val itemId: Long,

	/** 지급 코인 개수. */
	@Column(name = "coin_amount", nullable = false)
	val coinAmount: Int,

	/** PG 거래 식별자(paymentKey). PG 인증마다 고유 — 이중 제출의 이중 기록을 막기 위해 유니크다. */
	@Column(name = "payment_key", nullable = false, unique = true)
	val paymentKey: String,

	/** 가맹점 주문번호(orderId). 결제 요청 시 생성해 PG confirm·조회에 쓴다. */
	@Column(name = "order_id", nullable = false)
	val orderId: String,

	/** 서버 확정 실결제가(원). */
	@Column(name = "amount", nullable = false)
	val amount: Int,

	/** 결제(PG 청구) 라이프사이클 상태. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: PaymentStatus,

	/** PG 승인 실패 사유(응답 원문). 실패 추적용이며 성공 시 null. 컬럼 길이 초과분은 저장 시 잘린다. */
	@Column(name = "fail_reason", length = FAIL_REASON_MAX_LENGTH)
	var failReason: String? = null,
) : BaseEntity() {

	companion object {
		const val FAIL_REASON_MAX_LENGTH: Int = 1000
	}
}
