package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 구성원 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 구성원 저장·팀별 구성원 조회(파생 쿼리)를 담당한다.
 * [com.org.meeple.infra.match.command.adapter.TeamAdapter]가 팀 애그리거트(헤더+구성원) 영속화에서 사용한다.
 */
interface TeamMemberJpaRepository : JpaRepository<TeamMemberEntity, Long> {

	/** 한 팀의 구성원 전체. (팀 애그리거트 조립용) */
	fun findByTeamId(teamId: Long): List<TeamMemberEntity>

	/** [userId]가 (삭제되지 않은) 팀 구성원으로 존재하는지 여부. (@SQLRestriction이 삭제행을 제외) */
	fun existsByUserId(userId: Long): Boolean
}
