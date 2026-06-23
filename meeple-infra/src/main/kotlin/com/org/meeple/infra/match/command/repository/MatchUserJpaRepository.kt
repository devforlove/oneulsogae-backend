package com.org.meeple.infra.match.command.repository

import com.org.meeple.common.user.Gender
import com.org.meeple.infra.match.command.entity.MatchUserEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 리포지토리. PK는 생성 id지만 비즈니스 키는 user_id이므로 단건 조회는 [findByUserId]로 한다.
 * 후보 조회는 가까운 지역부터 지역 단위로, 반대 성별·최근 로그인·재소개 이력 없음 조건으로 한다.
 */
interface MatchUserJpaRepository : JpaRepository<MatchUserEntity, Long> {

	/** 비즈니스 키(user_id)로 단건 조회한다. (upsert 분기·매칭 가능 판정에 쓴다) */
	fun findByUserId(userId: Long): MatchUserEntity?

	/**
	 * 한 지역([regionId]) 안에서 반대 성별([gender])·최근 로그인([loginAfter] 이후) 후보 중,
	 * 요청자([requesterId])와 함께 소개된 이력이 없는(재소개 방지) 후보를 최근 로그인 우선으로 조회한다.
	 * 호출 측이 [pageable]로 1건만 가져가 "이 지역의 최근접 신선 후보"로 쓴다. (가까운 지역부터 지역 단위로 순회)
	 * 이력 제외는 두 사람이 한 매칭(solo_match)에 함께 참가한 적이 있는지로 판단한다. (소프트 삭제된 매칭은 @SQLRestriction으로 제외 — existsByPair와 동일 의미)
	 */
	@Query(
		"""
		select m.userId
		from MatchUserEntity m
		where m.gender = :gender
		  and m.regionId = :regionId
		  and m.lastLoginAt >= :loginAfter
		  and not exists (
		      select 1
		      from SoloMatchMemberEntity me
		      join SoloMatchMemberEntity other on other.matchId = me.matchId
		      where me.userId = :requesterId
		        and other.userId = m.userId
		  )
		order by m.lastLoginAt desc
		""",
	)
	fun findNearestCandidateInRegion(
		@Param("requesterId") requesterId: Long,
		@Param("gender") gender: Gender,
		@Param("regionId") regionId: Long,
		@Param("loginAfter") loginAfter: LocalDateTime,
		pageable: Pageable,
	): List<Long>

	/** 이미 적재된 사용자의 마지막 로그인 시각만 갱신한다. 영향 행 수를 반환한다(행이 없으면 0 = no-op). */
	@Modifying
	@Query(
		"""
		update MatchUserEntity m
		set m.lastLoginAt = :lastLoginAt
		where m.userId = :userId
		""",
	)
	fun updateLastLoginAt(@Param("userId") userId: Long, @Param("lastLoginAt") lastLoginAt: LocalDateTime): Int

	/** 해당 사용자의 행을 삭제한다. 영향 행 수를 반환한다(행이 없으면 0 = no-op, 예외 없음). */
	@Modifying
	@Query("delete from MatchUserEntity m where m.userId = :userId")
	fun deleteByUserId(@Param("userId") userId: Long): Int
}
