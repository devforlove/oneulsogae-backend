package com.org.meeple.infra.gathering.command.entity

import com.org.meeple.common.gathering.GatheringProductType
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
 * 한 일정([GatheringScheduleEntity])의 성별·티어별 가격 한 건(상품)을 담는 영속성 엔티티.
 * 한 일정이 성별(2) × 타입(1~3) 조합으로 2~6행을 가진다. 소속 일정은 [scheduleId], 모임은 [gatheringId]로 참조한다.
 * 얼리버드가도 할인율이 아니라 생성 시점에 계산된 확정 금액([price])으로 저장한다.
 * 얼리버드 선착순 수량은 남/녀 공유 카운터라 일정 테이블(early_bird_capacity/remaining)에 남아있다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gathering_products",
	indexes = [
		// 일정별 상품 조회용 인덱스. (schedule_id 동등/IN 조건)
		Index(name = "idx_schedule_id", columnList = "schedule_id"),
	],
)
class GatheringProductEntity(
	/** 소속 모임 id. */
	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	/** 소속 일정 id. */
	@Column(name = "schedule_id", nullable = false)
	val scheduleId: Long,

	/** 가격 적용 성별. */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	val gender: Gender,

	/** 가격 타입(정가·얼리버드가·할인가). */
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	val type: GatheringProductType,

	/** 확정 금액(원). 0이면 무료. */
	@Column(name = "price", nullable = false)
	val price: Int,
) : BaseEntity()
