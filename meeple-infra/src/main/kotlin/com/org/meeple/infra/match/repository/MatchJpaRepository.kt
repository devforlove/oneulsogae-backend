package com.org.meeple.infra.match.repository

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.infra.match.entity.MatchEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/**
 * 매칭 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * core 모듈용 어댑터 [com.org.meeple.infra.match.adapter.MatchCoreAdapter]가 이 리포지토리로 단건/존재 조회·저장을 구현한다.
 */
interface MatchJpaRepository : JpaRepository<MatchEntity, Long> {

	/**
	 * 매칭+상대 프로필 조인 조회는 성별에 따라 조회/상대 컬럼이 갈려 QueryDSL로 처리한다.
	 * ([com.org.meeple.infra.match.adapter.MatchQueryCoreAdapter.findAllWithPartnerByUserId])
	 */

	/** [introducedDate]에 남자 측으로 소개된 매칭 존재 여부. (idx_male_user_id_introduced_date 사용) */
	fun existsByMaleUserIdAndIntroducedDate(maleUserId: Long, introducedDate: LocalDate): Boolean

	/** [introducedDate]에 여자 측으로 소개된 매칭 존재 여부. (idx_female_user_id_introduced_date 사용) */
	fun existsByFemaleUserIdAndIntroducedDate(femaleUserId: Long, introducedDate: LocalDate): Boolean

	/** 해당 남녀 쌍으로 소개된 이력 존재 여부. (udx_male_user_id_female_user_id 사용, 재소개 방지) */
	fun existsByMaleUserIdAndFemaleUserId(maleUserId: Long, femaleUserId: Long): Boolean

	/**
	 * [status] 상태인 매칭의 (남자 ID, 여자 ID) 쌍을 모두 조회한다. (배치의 매칭 사용자 제외 집합 산출용)
	 * 두 컬럼만 가볍게 프로젝션하며, 양쪽 ID 펼치기·중복 정리는 어댑터/도메인에서 한다.
	 */
	@Query(
		"""
		select m.maleUserId as maleUserId, m.femaleUserId as femaleUserId
		from MatchEntity m
		where m.status = :status
		""",
	)
	fun findUserIdPairsByStatus(@Param("status") status: MatchStatus): List<MatchedPairView>
}

/** [MatchJpaRepository.findUserIdPairsByStatus]의 인터페이스 프로젝션. */
interface MatchedPairView {
	val maleUserId: Long
	val femaleUserId: Long
}
