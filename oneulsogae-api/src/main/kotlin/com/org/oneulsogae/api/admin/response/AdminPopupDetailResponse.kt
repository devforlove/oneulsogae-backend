package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.popup.query.dto.AdminPopupDetailView
import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/** 어드민 팝업 상세 응답. 목록 필드 + 본문·링크·버튼. 이미지는 코드가 아니라 미리보기 URL로 내려준다. */
data class AdminPopupDetailResponse(
	val id: Long,
	val title: String?,
	val description: String?,
	val displayOrder: Int,
	/** 팝업 이미지 URL. 이미지가 없으면 null. */
	val imageUrl: String?,
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
				imageUrl = view.imageUrl,
				linkUrl = view.linkUrl,
				buttonText = view.buttonText,
				popUpType = view.popUpType,
				exposedFrom = view.exposedFrom,
				exposedTo = view.exposedTo,
				createdAt = view.createdAt,
			)
	}
}
