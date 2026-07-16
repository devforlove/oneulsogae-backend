package com.org.meeple.infra.gathering.command.repository

import com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringMemberJpaRepository : JpaRepository<GatheringMemberEntity, Long> {

	/** (schedule, user)의 참가 행을 조회한다. (schedule_id, user_id) 유니크라 최대 1건. */
	fun findByScheduleIdAndUserId(scheduleId: Long, userId: Long): GatheringMemberEntity?
}
