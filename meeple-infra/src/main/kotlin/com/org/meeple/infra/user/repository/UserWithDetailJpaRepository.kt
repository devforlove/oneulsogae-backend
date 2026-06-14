package com.org.meeple.infra.user.repository

import com.org.meeple.infra.user.entity.UserDetailEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 사용자 + 프로필 상세 조인 조회 전용 리포지토리.
 * 도메인 포트([com.org.meeple.core.user.application.port.out.GetUserWithDetailPort])는
 * [com.org.meeple.infra.user.adapter.UserDetailCoreAdapter]가 구현한다.
 */
interface UserWithDetailJpaRepository : JpaRepository<UserDetailEntity, Long> {

	/**
	 * 사용자와 프로필 상세를 조인해 함께 조회한다. (1+N 방지)
	 * 행은 [u=UserEntity, d=UserDetailEntity] 두 엔티티로 반환된다. (최대 1행)
	 */
	@Query(
		"""
		select u, d
		from UserEntity u
		join UserDetailEntity d on d.userId = u.id
		where u.id = :userId
		""",
	)
	fun findWithDetailByUserId(@Param("userId") userId: Long): List<Array<Any>>
}
