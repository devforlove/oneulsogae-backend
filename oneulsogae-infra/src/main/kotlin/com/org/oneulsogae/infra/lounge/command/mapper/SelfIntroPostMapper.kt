package com.org.oneulsogae.infra.lounge.command.mapper

import com.org.oneulsogae.core.lounge.command.domain.SelfIntroPost
import com.org.oneulsogae.infra.lounge.command.entity.SelfIntroPostEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun SelfIntroPostEntity.toDomain(): SelfIntroPost =
	SelfIntroPost(
		id = id ?: 0,
		postId = postId,
		mbti = mbti,
		interests = interests,
		idealType = idealType,
		charmPoint = charmPoint,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun SelfIntroPost.toEntity(): SelfIntroPostEntity =
	SelfIntroPostEntity(
		postId = postId,
		mbti = mbti,
		interests = interests,
		idealType = idealType,
		charmPoint = charmPoint,
	).also { if (id != 0L) it.id = id }
