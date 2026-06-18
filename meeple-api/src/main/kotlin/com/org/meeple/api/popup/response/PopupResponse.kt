package com.org.meeple.api.popup.response

import com.org.meeple.common.popup.PopupType
import com.org.meeple.core.popup.query.dto.PopupView
import com.org.meeple.core.popup.query.dto.PopupViews

/**
 * 팝업 응답. 노출 중인 팝업 한 건을 담는다.
 * [imageUrl]/[imageWidth]/[imageHeight]/[linkUrl]/[buttonText]는 없으면 null이며, [popUpType]은 팝업 유형(일반/출석 보상 등)이다.
 */
data class PopupResponse(
	val id: Long,
	val title: String?,
	val description: String?,
	val displayOrder: Int,
	val imageUrl: String?,
	val imageWidth: Int?,
	val imageHeight: Int?,
	val linkUrl: String?,
	val buttonText: String?,
	val popUpType: PopupType,
) {
	companion object {
		private fun of(popup: PopupView): PopupResponse =
			PopupResponse(
				id = popup.id,
				title = popup.title,
				description = popup.description,
				displayOrder = popup.displayOrder,
				imageUrl = popup.imageUrl,
				imageWidth = popup.imageWidth,
				imageHeight = popup.imageHeight,
				linkUrl = popup.linkUrl,
				buttonText = popup.buttonText,
				popUpType = popup.popUpType,
			)

		/** 팝업 목록을 응답 목록으로 변환한다. (display_order 정렬은 조회 단계에서 보장된다) */
		fun listOf(popups: PopupViews): List<PopupResponse> = popups.values.map { popup: PopupView -> of(popup) }
	}
}
