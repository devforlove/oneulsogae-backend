package com.org.meeple.infra.popup.command.mapper

import com.org.meeple.core.popup.command.domain.Popup
import com.org.meeple.infra.popup.command.entity.PopupEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun PopupEntity.toDomain(): Popup =
	Popup(
		id = id ?: 0,
		title = title,
		description = description,
		displayOrder = displayOrder,
		imageCode = imageCode,
		linkUrl = linkUrl,
		buttonText = buttonText,
		popUpType = popUpType,
		userId = userId,
		exposedFrom = exposedFrom,
		exposedTo = exposedTo,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun Popup.toEntity(): PopupEntity =
	PopupEntity(
		title = title,
		description = description,
		displayOrder = displayOrder,
		imageCode = imageCode,
		linkUrl = linkUrl,
		buttonText = buttonText,
		popUpType = popUpType,
		userId = userId,
		exposedFrom = exposedFrom,
		exposedTo = exposedTo,
	).also { if (id != 0L) it.id = id }
