package com.org.meeple.infra.alarm.command.mapper

import com.org.meeple.core.alarm.command.domain.Alarm
import com.org.meeple.infra.alarm.command.entity.AlarmEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun AlarmEntity.toDomain(): Alarm =
	Alarm(
		id = id ?: 0,
		userId = userId,
		type = type,
		title = title,
		description = description,
		link = link,
		fromUserId = fromUserId,
		fromTeamId = fromTeamId,
		isRead = isRead,
		createdAt = createdAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun Alarm.toEntity(): AlarmEntity =
	AlarmEntity(
		userId = userId,
		type = type,
		title = title,
		description = description,
		link = link,
		fromUserId = fromUserId,
		fromTeamId = fromTeamId,
		isRead = isRead,
	).also { if (id != 0L) it.id = id }
