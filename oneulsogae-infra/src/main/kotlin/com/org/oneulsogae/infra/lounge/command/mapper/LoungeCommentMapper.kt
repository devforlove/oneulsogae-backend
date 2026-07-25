package com.org.oneulsogae.infra.lounge.command.mapper

import com.org.oneulsogae.core.lounge.command.domain.LoungeComment
import com.org.oneulsogae.infra.lounge.command.entity.LoungeCommentEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun LoungeCommentEntity.toDomain(): LoungeComment =
	LoungeComment(
		id = id ?: 0,
		postId = postId,
		userId = userId,
		parentId = parentId,
		content = content,
		deleted = isDeleted,
		createdAt = createdAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deleted_at은 옮기지 않는다 — 수정은 도메인이 삭제 행을 막고([LoungeComment.editBy]),
 * 삭제는 어댑터가 엔티티를 로드해 softDelete로만 처리한다.
 */
fun LoungeComment.toEntity(): LoungeCommentEntity =
	LoungeCommentEntity(
		postId = postId,
		userId = userId,
		parentId = parentId,
		content = content,
	).also { if (id != 0L) it.id = id }
