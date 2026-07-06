package com.org.meeple.infra.gathering.command.repository

import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringScheduleJpaRepository : JpaRepository<GatheringScheduleEntity, Long>
