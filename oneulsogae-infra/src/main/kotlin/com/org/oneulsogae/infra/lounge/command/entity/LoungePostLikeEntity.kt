package com.org.oneulsogae.infra.lounge.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 라운지 글([LoungePostEntity])에 누른 좋아요 한 건을 (post_id, user_id) 한 쌍의 행으로 보관한다.
 * (post_id, user_id) 유니크 제약이 중복 좋아요를 막고, 동시에 "내가 눌렀는지" 조회도 커버한다.
 * **좋아요 취소는 soft delete가 아니라 행 삭제**다. 삭제 행이 남으면 다시 좋아요할 때 유니크 제약과 충돌하기 때문에
 * 이 엔티티만 `@SQLRestriction`을 두지 않는다. (표시용 총합은 [LoungePostEntity.likeCount]가 보관)
 */
@Entity
@Table(
	name = "lounge_post_likes",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_post_id_user_id", columnNames = ["post_id", "user_id"]),
	],
)
class LoungePostLikeEntity(
	/** 좋아요를 누른 라운지 글([LoungePostEntity])의 id. */
	@Column(name = "post_id", nullable = false)
	val postId: Long,

	/** 좋아요를 누른 사용자. */
	@Column(name = "user_id", nullable = false)
	val userId: Long,
) : BaseEntity()
