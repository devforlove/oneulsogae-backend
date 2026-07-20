package com.org.meeple.infra.lounge.command.mapper

import com.org.meeple.core.lounge.command.domain.LoungePost
import com.org.meeple.infra.lounge.command.entity.LoungePostEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun LoungePostEntity.toDomain(): LoungePost =
	LoungePost(
		id = id ?: 0,
		type = type,
		userId = userId,
		likeCount = likeCount,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun LoungePost.toEntity(): LoungePostEntity =
	LoungePostEntity(
		type = type,
		userId = userId,
		likeCount = likeCount,
	).also { if (id != 0L) it.id = id }
