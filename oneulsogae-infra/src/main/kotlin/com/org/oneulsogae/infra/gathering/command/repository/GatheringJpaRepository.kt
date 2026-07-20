package com.org.oneulsogae.infra.gathering.command.repository

import com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringJpaRepository : JpaRepository<GatheringEntity, Long>
