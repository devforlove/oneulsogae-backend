package com.org.meeple.infra.match.command.repository

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.user.entity.UserEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 후보(사용자) 조회 전용 리포지토리.
 * users + user_details를 조인해 반대 성별·정식 가입·최근 로그인 사용자를 조회한다.
 * 후보 수([countCandidates])를 센 뒤 [0, count) 랜덤 오프셋으로 한 명만 뽑는 방식(LIMIT offset, 1)을 위해,
 * 조회는 id 정렬 + [Pageable]로 오프셋/리밋을 받는다. (id 분포의 구멍과 무관하게 후보가 균등하게 선택된다)
 * (마지막 로그인이 [loginAfter] 이전이거나 없는(null) 사용자는 비교에서 빠져 후보에서 제외된다)
 * (@SQLRestriction 으로 두 엔티티의 soft delete된 행은 자동 제외된다)
 */
interface MatchCandidateJpaRepository : JpaRepository<UserEntity, Long> {

	/** 후보 풀(반대 성별·정식 가입·최근 로그인)의 사용자 수. 랜덤 오프셋의 상한으로 쓴다. */
	@Query(
		"""
		select count(u.id)
		from UserEntity u
		join UserDetailEntity d on d.userId = u.id
		where u.status = :status
		  and d.gender = :gender
		  and d.regionCode = :regionCode
		  and u.lastLoginAt >= :loginAfter
		""",
	)
	fun countCandidates(
		@Param("status") status: UserStatus,
		@Param("gender") gender: Gender,
		@Param("regionCode") regionCode: Int,
		@Param("loginAfter") loginAfter: LocalDateTime,
	): Long

	/** 후보를 id 오름차순으로 [pageable]의 오프셋/리밋만큼 조회한다. (오프셋 1·리밋 1로 랜덤 한 명 선택) */
	@Query(
		"""
		select u.id
		from UserEntity u
		join UserDetailEntity d on d.userId = u.id
		where u.status = :status
		  and d.gender = :gender
		  and d.regionCode = :regionCode
		  and u.lastLoginAt >= :loginAfter
		order by u.id asc
		""",
	)
	fun findCandidateUserIds(
		@Param("status") status: UserStatus,
		@Param("gender") gender: Gender,
		@Param("regionCode") regionCode: Int,
		@Param("loginAfter") loginAfter: LocalDateTime,
		pageable: Pageable,
	): List<Long>
}
