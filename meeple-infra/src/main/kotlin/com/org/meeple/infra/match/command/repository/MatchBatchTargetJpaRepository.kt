package com.org.meeple.infra.match.command.repository

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.user.command.entity.UserEntity
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 배치 대상(사용자) 조회 전용 리포지토리.
 * 정식 가입(ACTIVE) + 최근 로그인 사용자를 (lastLoginAt, id) 복합 키셋 페이징으로 조회하며,
 * 매칭 판단에 필요한 프로필(gender/age/maritalStatus/regionCode)을 user_details와 조인해 함께 가져온다.
 * `users(status, last_login_at, id)` 인덱스로 status 등치 + 최근 로그인 범위만 스캔하고, 정렬도 인덱스로 충족해 filesort가 없다.
 * 커서 유무로 [findFirstTargets]/[findNextTargets]를 나눠, `cursor is null OR ...` 형태가 range seek를 방해하지 않게 한다.
 * (@SQLRestriction 으로 두 엔티티의 soft delete된 행은 자동 제외된다)
 */
interface MatchBatchTargetJpaRepository : JpaRepository<UserEntity, Long> {

	/** 첫 페이지. 커서 없이 정식 가입(ACTIVE) + 최근 로그인([loginAfter] 이후) 사용자를 (lastLoginAt, id) 순으로 조회한다. */
	@Query(
		"""
		select u.id as userId, u.lastLoginAt as lastLoginAt,
		       d.gender as gender, d.age as age, d.maritalStatus as maritalStatus, d.regionCode as regionCode
		from UserEntity u
		join UserDetailEntity d on d.userId = u.id
		where u.status = :status
		  and u.lastLoginAt >= :loginAfter
		order by u.lastLoginAt asc, u.id asc
		""",
	)
	fun findFirstTargets(
		@Param("status") status: UserStatus,
		@Param("loginAfter") loginAfter: LocalDateTime,
		limit: Limit,
	): List<MatchBatchTargetView>

	/**
	 * 다음 페이지. 직전 페이지 마지막 행의 (cursorLastLogin, cursorId) 이후부터 조회한다.
	 * 커서는 항상 loginAfter 이후라 키셋 조건이 최근 로그인 하한도 함께 보장하므로 `lastLoginAt >= loginAfter`는 생략한다.
	 */
	@Query(
		"""
		select u.id as userId, u.lastLoginAt as lastLoginAt,
		       d.gender as gender, d.age as age, d.maritalStatus as maritalStatus, d.regionCode as regionCode
		from UserEntity u
		join UserDetailEntity d on d.userId = u.id
		where u.status = :status
		  and (
		    u.lastLoginAt > :cursorLastLogin
		    or (u.lastLoginAt = :cursorLastLogin and u.id > :cursorId)
		  )
		order by u.lastLoginAt asc, u.id asc
		""",
	)
	fun findNextTargets(
		@Param("status") status: UserStatus,
		@Param("cursorLastLogin") cursorLastLogin: LocalDateTime,
		@Param("cursorId") cursorId: Long,
		limit: Limit,
	): List<MatchBatchTargetView>
}

/**
 * [MatchBatchTargetJpaRepository.findTargets]의 인터페이스 프로젝션.
 * 다음 커서 산출에 lastLoginAt이, 매칭 판단에 gender/age/maritalStatus/regionCode가 필요하다.
 */
interface MatchBatchTargetView {
	val userId: Long
	val lastLoginAt: LocalDateTime
	val gender: Gender?
	val age: Int?
	val maritalStatus: MaritalStatus?
	val regionCode: Int?
}
