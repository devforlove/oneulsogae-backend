package com.org.meeple.infra.gathering.command.entity

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 한 모임([GatheringEntity])에 속한 일정(세션) 하나를 담는 영속성 엔티티.
 * 한 모임이 여러 일정을 가지므로 gatherings : gathering_schedules = 1 : N이고, 소속 모임은 [gatheringId]로 참조한다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로 표현하고, 생성 시 status는 예정([GatheringScheduleStatus.SCHEDULED])이다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gathering_schedules",
	indexes = [
		// 모임별 일정을 시작 시각순으로 조회하기 위한 인덱스. (gathering_id 동등 조건 + start_at 정렬)
		Index(name = "idx_gathering_id_start_at", columnList = "gathering_id, start_at"),
	],
)
class GatheringScheduleEntity(
	/** 소속 모임 id. */
	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	/** 일정 시작 시각. */
	@Column(name = "start_at", nullable = false)
	var startAt: LocalDateTime,

	/** 일정 종료 시각. 미정이면 null. */
	@Column(name = "end_at")
	var endAt: LocalDateTime? = null,

	/** 일정 진행 상태. 예정으로 시작한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
) : BaseEntity()
