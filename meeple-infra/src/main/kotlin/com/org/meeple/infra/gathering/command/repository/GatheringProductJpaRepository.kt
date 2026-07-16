package com.org.meeple.infra.gathering.command.repository

import com.org.meeple.infra.gathering.command.entity.GatheringProductEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringProductJpaRepository : JpaRepository<GatheringProductEntity, Long> {

	/** 한 일정의 상품 전부를 조회한다. (성별 2 × 타입 1~3 = 2~6행) */
	fun findByScheduleId(scheduleId: Long): List<GatheringProductEntity>
}
