package com.org.oneulsogae.infra.mission.command.repository

import com.org.oneulsogae.infra.mission.command.entity.MissionEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 미션 정의 리포지토리. 조회 포트는 query dao 구현이 담당한다. */
interface MissionJpaRepository : JpaRepository<MissionEntity, Long>
