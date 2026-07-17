package com.org.meeple.infra.payments.command.entity

import com.org.meeple.common.user.Gender
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 결제 기록 한 건. 무검증 접수 단계라 결제수단·PG 정보 없이 접수 내용(누가·어느 일정·얼마)만 보관한다.
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

	/** PG 거래 식별자(paymentKey). 승인 성공 건만 저장된다. */
	@Column(name = "payment_key", nullable = false)
	val paymentKey: String,

	/** 접수 성별(금액 티어 근거). */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	val gender: Gender,

	/** 서버 확정 실결제가(원). */
	@Column(name = "amount", nullable = false)
	val amount: Int,
) : BaseEntity()
