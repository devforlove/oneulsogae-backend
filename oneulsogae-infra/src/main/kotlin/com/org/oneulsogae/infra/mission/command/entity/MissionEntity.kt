package com.org.oneulsogae.infra.mission.command.entity

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 미션 정의 영속성 엔티티. 유형(type)·보상 코인·문구·활성 여부·노출 순서를 보관한다.
 * 자격 판정 로직은 두지 않는다(유형별 평가자가 코드로 판정). 삭제는 soft delete(deleted_at).
 * (type) 유니크 — 한 유형당 한 미션.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "missions",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_missions_type", columnNames = ["type"]),
	],
)
class MissionEntity(
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	val type: MissionType,

	/** 완료 시 지급 코인. */
	@Column(name = "reward_coin", nullable = false)
	var rewardCoin: Int,

	/** 목록 제목. */
	@Column(name = "title", nullable = false, columnDefinition = "varchar(100)")
	var title: String,

	/** 목록 설명. */
	@Column(name = "description", columnDefinition = "varchar(255)")
	var description: String? = null,

	/** 노출·수령 가능 여부. */
	@Column(name = "active", nullable = false)
	var active: Boolean = true,

	/** 목록 정렬(오름차순). */
	@Column(name = "display_order", nullable = false)
	var displayOrder: Int = 0,
) : BaseEntity()
