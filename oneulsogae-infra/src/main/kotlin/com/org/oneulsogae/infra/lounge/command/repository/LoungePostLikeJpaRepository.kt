package com.org.oneulsogae.infra.lounge.command.repository

import com.org.oneulsogae.infra.lounge.command.entity.LoungePostLikeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 라운지 글 좋아요 JPA 리포지토리. */
interface LoungePostLikeJpaRepository : JpaRepository<LoungePostLikeEntity, Long> {

	/**
	 * (post_id, user_id) 좋아요 행이 없으면 넣고 1, 이미 있으면(유니크 제약 ux_post_id_user_id) 아무것도 하지 않고 0을 반환한다.
	 * MySQL `INSERT IGNORE`라 동시 요청이 겹쳐도 예외 없이 한 건만 들어간다.
	 * (JPA insert 후 제약 위반을 잡는 방식은 트랜잭션이 rollback-only로 물들어 쓸 수 없다)
	 */
	@Modifying
	@Query(
		value = "insert ignore into lounge_post_likes (post_id, user_id, created_at, updated_at) values (:postId, :userId, now(), now())",
		nativeQuery = true,
	)
	fun insertIgnore(@Param("postId") postId: Long, @Param("userId") userId: Long): Int

	/** (post_id, user_id) 좋아요 행을 지우고 지운 행 수를 반환한다. (좋아요 취소는 행 삭제다) */
	fun deleteByPostIdAndUserId(postId: Long, userId: Long): Long
}
