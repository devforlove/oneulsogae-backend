package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 추천 팀(recommended_teams) 리포지토리. PK는 생성 id지만 교체(upsert) 키는 user_id이므로 [findByUserId]로 단건 조회한다.
 */
interface RecommendedTeamJpaRepository : JpaRepository<RecommendedTeamEntity, Long> {

	/** 교체(upsert) 분기를 위해 user_id로 기존 추천 행을 조회한다. */
	fun findByUserId(userId: Long): RecommendedTeamEntity?
}
