package com.org.oneulsogae.infra.lounge.command.repository

import com.org.oneulsogae.common.lounge.LoungePostType
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 라운지 글 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.oneulsogae.infra.lounge.command.adapter.LoungePostAdapter]가 구현한다.
 */
interface LoungePostJpaRepository : JpaRepository<LoungePostEntity, Long> {

	/** 유저가 [since] 이후에 등록한 해당 타입 글 수. (idx_user_id로 user_id를 seek한 뒤 타입·시각을 필터한다) */
	fun countByUserIdAndTypeAndCreatedAtAfter(userId: Long, type: LoungePostType, since: LocalDateTime): Int

	/** 좋아요 총합을 [delta]만큼 원자적으로 증감한다. (동시 요청이 겹쳐도 어긋나지 않는다) */
	@Modifying
	@Query("update LoungePostEntity p set p.likeCount = p.likeCount + :delta where p.id = :postId")
	fun updateLikeCount(@Param("postId") postId: Long, @Param("delta") delta: Int)

	/** 조회수를 1 원자적으로 올린다. 없는 글이면 0행에 적용돼 no-op이다. */
	@Modifying
	@Query("update LoungePostEntity p set p.viewCount = p.viewCount + 1 where p.id = :postId")
	fun incrementViewCount(@Param("postId") postId: Long)
}
