package com.org.meeple.infra.payments.command.entity

import com.org.meeple.common.user.Gender
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 결제 기록 한 건. 접수 시점에 PENDING으로 저장하고 PG 승인 결과로 APPROVED/FAILED로 전이한다 — 결제수단 등 상세는 없고 접수 내용(누가·어느 일정·얼마)만 보관한다.
 * 재접수(거절 후 다시 결제완료)마다 새 행이 쌓인다 — (schedule_id, user_id)는 유니크가 아니다.
 * (schedule_id, user_id) 인덱스로 일정별 참가자 목록의 결제금액 조인을 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "payments",
	indexes = [
		// 일정별 참가자의 결제 기록 조회.
		Index(name = "idx_schedule_id_user_id", columnList = "schedule_id, user_id"),
	],
)
class PaymentEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	@Column(name = "schedule_id", nullable = false)
	val scheduleId: Long,

	/** 결제완료 요청의 상품 id(가격 근거). 요청이 지정한 성별 정가(NORMAL) 상품을 가리킨다. */
	@Column(name = "product_id", nullable = false)
	val productId: Long,

	/** PG 거래 식별자(paymentKey). PG 인증마다 고유 — 이중 제출의 이중 기록을 막기 위해 유니크다. */
	@Column(name = "payment_key", nullable = false, unique = true)
	val paymentKey: String,

	/** 가맹점 주문번호(orderId). 결제 요청 시 생성해 PG confirm·조회에 쓴다. */
	@Column(name = "order_id", nullable = false)
	val orderId: String,

	/** 접수 성별(금액 티어 근거). */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	val gender: Gender,

	/** 서버 확정 실결제가(원). */
	@Column(name = "amount", nullable = false)
	val amount: Int,

	/** 결제(PG 청구) 라이프사이클 상태. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: PaymentStatus,
) : BaseEntity()
