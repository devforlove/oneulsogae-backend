package com.org.meeple.infra.teammatch.command.repository

import com.org.meeple.common.user.Gender
import com.org.meeple.infra.teammatch.command.entity.TeamEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 팀 헤더 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * [com.org.meeple.infra.teammatch.command.adapter.TeamAdapter]가 팀 애그리거트(헤더+구성원) 영속화에서 사용한다.
 */
interface TeamJpaRepository : JpaRepository<TeamEntity, Long> {

	/** 주어진 성별([gender])의 결성(ACTIVE) 팀이 하나라도 있는 region_id 목록. ([com.org.meeple.infra.region.TeamPopulatedRegionRegistry]가 성별별 스냅샷으로 캐시해 후보 팀 없는 region 건너뛰기에 쓴다) */
	@Query("select distinct t.regionId from TeamEntity t where t.gender = :gender and t.status = com.org.meeple.common.match.TeamStatus.ACTIVE")
	fun findDistinctActiveRegionIdsByGender(@Param("gender") gender: Gender): List<Long>
}
