package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.MatchUserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 리포지토리. PK는 생성 id지만 비즈니스 키는 user_id이므로 단건 조회는 [findByUserId]로 한다.
 * 온보딩 후보 조회(근접 지역·반대 성별·이력 제외)는 동적 거리 정렬이 필요해 [com.org.meeple.infra.match.command.adapter.MatchUserAdapter]가 QueryDSL로 수행한다.
 */
interface MatchUserJpaRepository : JpaRepository<MatchUserEntity, Long> {

	/** 비즈니스 키(user_id)로 단건 조회한다. (upsert 분기·매칭 가능 판정에 쓴다) */
	fun findByUserId(userId: Long): MatchUserEntity?

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
