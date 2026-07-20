package com.org.oneulsogae.infra.coin.command.entity

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * coin_histories 테이블 영속성 엔티티.
 * 사용자가 코인을 구매하거나 무료로 획득할 때마다 한 건씩 적립 내역으로 쌓인다.
 * 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "coin_histories",
	indexes = [
		// (user_id, coin_get_type, occurred_at) 복합 인덱스.
		// DAILY 일일 중복 적립 조회(user_id + coin_get_type + occurred_at 범위)와
		// 타입별 최신 수령일 조회(user_id + coin_get_type, occurred_at 내림차순 1행)를 커버하며,
		// 최좌측 prefix(user_id)로 사용자별 적립 내역 조회(findByUserId)도 함께 커버한다.
		Index(name = "idx_user_id_coin_get_type_occurred_at", columnList = "user_id, coin_get_type, occurred_at"),
		// 사용자별 거래 내역 커서 페이징(user_id 동등 + id 내림차순 keyset)용.
		// (위 복합 인덱스는 user_id 내부가 coin_get_type·occurred_at순이라 id 정렬을 받쳐주지 못한다)
		Index(name = "idx_user_id_id", columnList = "user_id, id"),
	],
)
class CoinHistoryEntity(
	/** 코인을 보유한 사용자 id. */
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 적립 수량. */
	@Column(name = "amount", nullable = false)
	var amount: Int,

	/** 코인 획득(적립) 유형. (무료 획득/구매) 차감 내역에는 해당 없어 null이다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "coin_get_type", columnDefinition = "varchar(50)")
	var coinGetType: CoinGetType? = null,

	/** 코인 사용(차감) 작업 유형. (소개팅/미팅 신청·수락) 적립 내역에는 해당 없어 null이다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "coin_usage_type", columnDefinition = "varchar(50)")
	var coinUsageType: CoinUsageType? = null,

	/** 코인 거래가 발생한 시각. (획득/구매 완료 또는 사용 차감) */
	@Column(name = "occurred_at", nullable = false)
	var occurredAt: LocalDateTime,
) : BaseEntity()
