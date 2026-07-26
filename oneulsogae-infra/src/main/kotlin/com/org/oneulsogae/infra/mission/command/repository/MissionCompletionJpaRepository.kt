package com.org.oneulsogae.infra.mission.command.repository

import com.org.oneulsogae.infra.mission.command.entity.MissionCompletionEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 미션 완료 기록 리포지토리. 도메인 포트는 어댑터가 구현한다. */
interface MissionCompletionJpaRepository : JpaRepository<MissionCompletionEntity, Long>
