package com.org.meeple.infra.gathering.command.entity

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 한 모임 일정([GatheringScheduleEntity])에 참가 신청한 참가자 한 명을 (schedule_id, user_id) 한 쌍의 행으로 정규화한 엔티티.
 * 참가는 일정(회차) 단위다. 소속 모임은 [gatheringId]로 함께 보관한다(일정 조인 없이 모임 단위 조회 커버).
 * 주최자는 [GatheringEntity.userId]로만 표현하므로 이 엔티티는 순수 참가자만 보관한다.
 * (schedule_id, user_id) 유니크 제약으로 같은 사용자의 같은 일정 중복 신청을 막고, (user_id) 인덱스로 사용자별 참가 조회를 커버한다.
 * [earlyBirdApplied]는 접수 시 얼리버드 여분을 소진했는지 기록한다(거절 시 early_bird_remaining 복원 판단).
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gathering_members",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_schedule_id_user_id", columnNames = ["schedule_id", "user_id"]),
	],
	indexes = [
		// 사용자별 참가 모임 조회.
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class GatheringMemberEntity(
	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	@Column(name = "schedule_id", nullable = false)
	val scheduleId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 참가자 성별. 거절/취소 시 어느 성별 여분 카운터를 복원할지 판단한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	var gender: Gender,

	/** 참가자 상태. 승인대기·참가·거절·참가취소를 구분한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: GatheringMemberStatus,

	/** 접수 시 얼리버드 여분을 소진했는지 여부. */
	@Column(name = "early_bird_applied", nullable = false)
	var earlyBirdApplied: Boolean = false,
) : BaseEntity()
