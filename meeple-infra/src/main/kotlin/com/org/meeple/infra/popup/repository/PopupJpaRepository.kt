package com.org.meeple.infra.popup.repository

import com.org.meeple.infra.popup.entity.PopupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 팝업 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.popup.adapter.PopupRepositoryAdapter]가 구현한다.
 */
interface PopupJpaRepository : JpaRepository<PopupEntity, Long> {

	/**
	 * [now] 기준 노출 대상인 팝업을 display_order 오름차순(동순위는 id 오름차순)으로 조회한다.
	 * 노출 ON(exposed=true) + 노출 기간 내(시작/종료가 null이면 그쪽 제한 없음)인 행만 가져온다.
	 * (idx_exposed_display_order 사용)
	 */
	@Query(
		"""
		select p
		from PopupEntity p
		where p.exposed = true
		  and (p.exposedFrom is null or p.exposedFrom <= :now)
		  and (p.exposedTo is null or p.exposedTo >= :now)
		order by p.displayOrder asc, p.id asc
		""",
	)
	fun findVisible(@Param("now") now: LocalDateTime): List<PopupEntity>
}
