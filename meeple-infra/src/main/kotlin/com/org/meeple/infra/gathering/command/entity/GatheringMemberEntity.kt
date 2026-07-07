package com.org.meeple.infra.gathering.command.entity

import com.org.meeple.common.gathering.GatheringMemberStatus
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
 * 한 모임([GatheringEntity])에 참가한 참가자 한 명을 (gathering_id, user_id) 한 쌍의 행으로 정규화한 엔티티.
 * 한 모임에 여러 명이 참가하므로 gatherings : gathering_members = 1 : N이다.
 * 주최자는 [GatheringEntity.userId]로만 표현하므로 이 엔티티는 순수 참가자만 보관한다.
 * (gathering_id, user_id) 유니크 제약으로 같은 사용자의 중복 참가를 막고, (user_id) 인덱스로 사용자별 참가 모임 조회를 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gathering_members",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_gathering_id_user_id", columnNames = ["gathering_id", "user_id"]),
	],
	indexes = [
		// 사용자별 참가 모임 조회.
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class GatheringMemberEntity(
	@Column(name = "gathering_id", nullable = false)
	val gatheringId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 참가자 상태. 참가·참가취소를 구분한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: GatheringMemberStatus,
) : BaseEntity()
