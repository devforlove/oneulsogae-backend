package com.org.oneulsogae.infra.notification.command.mapper

import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import com.org.oneulsogae.infra.notification.command.entity.NotificationPreferenceEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun NotificationPreferenceEntity.toDomain(): NotificationPreference =
	NotificationPreference(
		id = id ?: 0,
		userId = userId,
		push = push,
		oneToOne = oneToOne,
		lounge = lounge,
		meeting = meeting,
		team = team,
		message = message,
		marketing = marketing,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규 INSERT, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun NotificationPreference.toEntity(): NotificationPreferenceEntity =
	NotificationPreferenceEntity(
		userId = userId,
		push = push,
		oneToOne = oneToOne,
		lounge = lounge,
		meeting = meeting,
		team = team,
		message = message,
		marketing = marketing,
	).also { if (id != 0L) it.id = id }
