package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.popup.query.dto.AdminPopupDetailView
import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/** 어드민 팝업 상세 응답. 목록 필드 + 본문·이미지·링크·버튼. */
data class AdminPopupDetailResponse(
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
) {
	companion object {
		fun of(view: AdminPopupDetailView): AdminPopupDetailResponse =
			AdminPopupDetailResponse(
				id = view.id,
				title = view.title,
				description = view.description,
				displayOrder = view.displayOrder,
				imageCode = view.imageCode,
				linkUrl = view.linkUrl,
				buttonText = view.buttonText,
				popUpType = view.popUpType,
				exposedFrom = view.exposedFrom,
				exposedTo = view.exposedTo,
				createdAt = view.createdAt,
			)
	}
}
