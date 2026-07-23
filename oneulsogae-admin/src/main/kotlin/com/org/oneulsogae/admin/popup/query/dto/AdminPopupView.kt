package com.org.oneulsogae.admin.popup.query.dto

import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/** 어드민 팝업 목록 한 건(read model). 본문·링크 등 상세 필드는 상세에서만 노출한다. */
data class AdminPopupView(
	val id: Long,
	val title: String?,
	val displayOrder: Int,
	val popUpType: PopupType,
	val exposedFrom: LocalDateTime,
	val exposedTo: LocalDateTime,
	val createdAt: LocalDateTime?,
)
