package com.org.oneulsogae.infra.solomatch.command.repository

import com.org.oneulsogae.infra.solomatch.command.entity.SoloMatchMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 참가자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 참가자 저장·매칭별 참가자 조회(파생 쿼리)를 담당한다.
 * [com.org.oneulsogae.infra.solomatch.command.adapter.MatchAdapter]가 매칭 애그리거트(헤더+참가자) 영속화에서 사용한다.
 * 참가자↔매칭 조인이 필요한 조회(성사 사용자 등)는 [com.org.oneulsogae.infra.solomatch.query]의 QueryDSL dao가 담당한다.
 */
interface MatchMemberJpaRepository : JpaRepository<SoloMatchMemberEntity, Long> {

	/** 한 매칭의 참가자 전체. (매칭 애그리거트 조립용) */
	fun findByMatchId(matchId: Long): List<SoloMatchMemberEntity>

	/**
	 * 참가자의 매칭 확인 시각을 미기록(null)인 경우에만 기록한다. 영향 행 수를 반환한다(이미 기록이면 0).
	 * (벌크 JPQL은 @SQLRestriction이 적용되지 않으므로 소프트 삭제 행 제외 조건을 직접 건다.
	 * ux_match_id_user_id 유니크 인덱스 seek 단건 갱신)
	 */
	@Modifying
	@Query(
		"""
		update SoloMatchMemberEntity m
		set m.checkedAt = :checkedAt
		where m.matchId = :matchId and m.userId = :userId and m.checkedAt is null and m.deletedAt is null
		""",
	)
	fun markCheckedIfUnchecked(@Param("matchId") matchId: Long, @Param("userId") userId: Long, @Param("checkedAt") checkedAt: LocalDateTime): Int
}
