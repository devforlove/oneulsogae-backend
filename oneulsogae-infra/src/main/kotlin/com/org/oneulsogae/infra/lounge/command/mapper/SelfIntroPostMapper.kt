package com.org.oneulsogae.infra.lounge.command.mapper

import com.org.oneulsogae.core.lounge.command.domain.SelfIntroPost
import com.org.oneulsogae.infra.lounge.command.entity.SelfIntroPostEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun SelfIntroPostEntity.toDomain(): SelfIntroPost =
	SelfIntroPost(
		id = id ?: 0,
		postId = postId,
		longDistance = longDistance,
		desiredAge = desiredAge,
		mbti = mbti,
		marriageThought = marriageThought,
		preferredPartner = preferredPartner,
		charmPoint = charmPoint,
		freeWord = freeWord,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun SelfIntroPost.toEntity(): SelfIntroPostEntity =
	SelfIntroPostEntity(
		postId = postId,
		longDistance = longDistance,
		desiredAge = desiredAge,
		mbti = mbti,
		marriageThought = marriageThought,
		preferredPartner = preferredPartner,
		charmPoint = charmPoint,
		freeWord = freeWord,
	).also { if (id != 0L) it.id = id }
