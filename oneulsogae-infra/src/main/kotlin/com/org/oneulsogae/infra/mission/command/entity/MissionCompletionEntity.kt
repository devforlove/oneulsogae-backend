package com.org.oneulsogae.infra.mission.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 미션 완료 기록(가드). 미션 보상을 실제 수령한 시점에 저장한다.
 * (user_id, mission_id) 유니크가 이중 수령을 원자적으로 막는다.
 * [rewardedCoin]은 수령 시점 보상 코인의 스냅샷이다(정의가 나중에 바뀌어도 이력 보존).
 */
@Entity
@Table(
	name = "mission_completions",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_mission_completions_user_mission", columnNames = ["user_id", "mission_id"]),
	],
)
class MissionCompletionEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "mission_id", nullable = false)
	val missionId: Long,

	@Column(name = "rewarded_coin", nullable = false)
	val rewardedCoin: Int,
) : BaseEntity()
