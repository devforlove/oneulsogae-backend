package com.org.meeple.infra.gathering.command.repository

import com.org.meeple.infra.gathering.command.entity.GatheringEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringJpaRepository : JpaRepository<GatheringEntity, Long>
