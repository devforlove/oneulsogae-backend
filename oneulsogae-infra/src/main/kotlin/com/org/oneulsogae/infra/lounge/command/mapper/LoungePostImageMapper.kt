package com.org.oneulsogae.infra.lounge.command.mapper

import com.org.oneulsogae.core.lounge.command.domain.LoungePostImage
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostImageEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun LoungePostImageEntity.toDomain(): LoungePostImage =
	LoungePostImage(
		id = id ?: 0,
		postId = postId,
		imageKey = imageKey,
		displayOrder = displayOrder,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun LoungePostImage.toEntity(): LoungePostImageEntity =
	LoungePostImageEntity(
		postId = postId,
		imageKey = imageKey,
		displayOrder = displayOrder,
	).also { if (id != 0L) it.id = id }
