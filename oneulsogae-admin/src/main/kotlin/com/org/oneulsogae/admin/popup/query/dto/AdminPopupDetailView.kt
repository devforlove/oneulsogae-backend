package com.org.oneulsogae.admin.popup.query.dto

import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/** 어드민 팝업 상세 read model. 목록 필드 + 본문·이미지·링크·버튼. */
data class AdminPopupDetailView(
	val id: Long,
	val title: String?,
	val description: String?,
	val displayOrder: Int,
	val imageCode: String?,
	val linkUrl: String?,
	val buttonText: String?,
	val popUpType: PopupType,
	val exposedFrom: LocalDateTime,
	val exposedTo: LocalDateTime,
	val createdAt: LocalDateTime?,
)
