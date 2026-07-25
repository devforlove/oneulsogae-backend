package com.org.oneulsogae.infra.lounge.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 라운지 글 댓글 영속성 엔티티. [parentId]가 null이면 댓글(root), 값이 있으면 대댓글이다(깊이 1단계).
 * 삭제는 soft delete(deleted_at)로 처리하되 **@SQLRestriction을 걸지 않는다** —
 * 살아있는 대댓글이 남은 삭제 root는 목록에 "삭제된 댓글"로 계속 노출해야 해서 삭제 행도 조회돼야 한다.
 * (삭제 행 제외는 조회 쿼리가 조건으로 직접 거른다)
 */
@Entity
@Table(
	name = "lounge_comments",
	indexes = [
		// 글별 root 댓글 목록(오래된 순) 조회용. 동등 조건(post_id, parent_id is null)과 정렬 컬럼(id)을 한 인덱스로 받친다.
		Index(name = "idx_post_id_parent_id_id", columnList = "post_id, parent_id, id"),
		// 대댓글 목록·살아있는 대댓글 존재 확인(exists)용.
		Index(name = "idx_parent_id", columnList = "parent_id"),
	],
)
class LoungeCommentEntity(
	/** 대상 라운지 글([LoungePostEntity])의 id. */
	@Column(name = "post_id", nullable = false)
	val postId: Long,

	/** 작성자. */
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 부모 댓글 id. null이면 댓글(root), 값이 있으면 대댓글이다. */
	@Column(name = "parent_id")
	val parentId: Long? = null,

	/** 댓글 내용. */
	@Column(name = "content", nullable = false, columnDefinition = "varchar(500)")
	var content: String,
) : BaseEntity()
