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
 * 후보 조회는 반대 성별·같은 권역·최근 로그인 조건만으로 한다. (행의 존재가 곧 정식 가입+프로필 완성이라 status·null 검사가 없다)
 * 후보 수([countCandidates])를 센 뒤 [0, count) 랜덤 오프셋으로 한 명만 뽑는 방식(LIMIT offset, 1)을 위해 id 정렬 + [Pageable]을 받는다.
 */
interface MatchUserJpaRepository : JpaRepository<MatchUserEntity, Long> {

	/** 비즈니스 키(user_id)로 단건 조회한다. (upsert 분기·매칭 가능 판정에 쓴다) */
	fun findByUserId(userId: Long): MatchUserEntity?

	/** 후보 풀(반대 성별·같은 권역·최근 로그인)의 사용자 수. 랜덤 오프셋의 상한으로 쓴다. */
	@Query(
		"""
		select count(m)
		from MatchUserEntity m
		where m.gender = :gender
		  and m.regionCode = :regionCode
		  and m.lastLoginAt >= :loginAfter
		""",
	)
	fun countCandidates(
		@Param("gender") gender: Gender,
		@Param("regionCode") regionCode: Int,
		@Param("loginAfter") loginAfter: LocalDateTime,
	): Long

	/** 후보를 user_id 오름차순으로 [pageable]의 오프셋/리밋만큼 조회한다. (오프셋 N·리밋 1로 랜덤 한 명 선택) */
	@Query(
		"""
		select m.userId
		from MatchUserEntity m
		where m.gender = :gender
		  and m.regionCode = :regionCode
		  and m.lastLoginAt >= :loginAfter
		order by m.userId asc
		""",
	)
	fun findCandidateUserIds(
		@Param("gender") gender: Gender,
		@Param("regionCode") regionCode: Int,
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
