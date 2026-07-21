package com.org.oneulsogae.infra.lounge.command.mapper

import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun LoungeChatRequestEntity.toDomain(): LoungeChatRequest =
	LoungeChatRequest(
		id = id ?: 0,
		postId = postId,
		requesterUserId = requesterUserId,
		receiverUserId = receiverUserId,
		status = status,
		createdAt = createdAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun LoungeChatRequest.toEntity(): LoungeChatRequestEntity =
	LoungeChatRequestEntity(
		postId = postId,
		requesterUserId = requesterUserId,
		receiverUserId = receiverUserId,
		status = status,
	).also { if (id != 0L) it.id = id }
