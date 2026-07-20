package com.org.oneulsogae.infra.matchuser.command.repository

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.matchuser.command.entity.MatchUserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 리포지토리. PK는 생성 id지만 비즈니스 키는 user_id이므로 단건 조회는 [findByUserId]로 한다.
 * 온보딩 후보 조회(근접 지역·반대 성별·이력 제외)는 동적 거리 정렬이 필요해 [com.org.oneulsogae.infra.matchuser.command.adapter.MatchUserAdapter]가 QueryDSL로 수행한다.
 */
interface MatchUserJpaRepository : JpaRepository<MatchUserEntity, Long> {

	/** 비즈니스 키(user_id)로 단건 조회한다. (upsert 분기·매칭 가능 판정에 쓴다) */
	fun findByUserId(userId: Long): MatchUserEntity?

	/**
	 * 주어진 성별([gender])이면서 [loginAfter] 이후 로그인한 매칭 유저가 한 명이라도 있는 region_id 목록.
	 * ([com.org.oneulsogae.infra.region.PopulatedRegionRegistry]가 성별별 스냅샷으로 캐시해 빈 region 건너뛰기에 쓴다)
	 * (gender 동등 + last_login_at 범위 + region_id 투영이 인덱스 `idx_gender_region_id_last_login_at`로 커버된다)
	 */
	@Query("select distinct m.regionId from MatchUserEntity m where m.gender = :gender and m.lastLoginAt >= :loginAfter")
	fun findDistinctRegionIdsByGender(@Param("gender") gender: Gender, @Param("loginAfter") loginAfter: LocalDateTime): List<Long>

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

	/** 같은 회사 소개 거부 플래그만 갱신한다. 영향 행 수를 반환한다(행이 없으면 0 = 미적재). */
	@Modifying
	@Query(
		"""
		update MatchUserEntity m
		set m.refuseSameCompanyIntro = :refuse
		where m.userId = :userId
		""",
	)
	fun updateRefuseSameCompanyIntro(@Param("userId") userId: Long, @Param("refuse") refuse: Boolean): Int

	/** 회사명만 갱신한다. 영향 행 수를 반환한다(행이 없으면 0 = 미적재, 예외 없음). */
	@Modifying
	@Query(
		"""
		update MatchUserEntity m
		set m.companyName = :companyName
		where m.userId = :userId
		""",
	)
	fun updateCompanyName(@Param("userId") userId: Long, @Param("companyName") companyName: String): Int

	/** 해당 사용자의 행을 삭제한다. 영향 행 수를 반환한다(행이 없으면 0 = no-op, 예외 없음). */
	@Modifying
	@Query("delete from MatchUserEntity m where m.userId = :userId")
	fun deleteByUserId(@Param("userId") userId: Long): Int
}
