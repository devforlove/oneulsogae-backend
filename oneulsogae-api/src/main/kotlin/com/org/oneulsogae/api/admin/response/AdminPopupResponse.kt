package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.popup.query.dto.AdminPopupView
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupViews
import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/** 어드민 팝업 목록 항목 응답. 본문·링크 등 상세 필드는 상세에서만 노출한다. */
data class AdminPopupResponse(
	val id: Long,
	val title: String?,
	val displayOrder: Int,
	val popUpType: PopupType,
	val exposedFrom: LocalDateTime,
	val exposedTo: LocalDateTime,
	val createdAt: LocalDateTime?,
) {
	companion object {
		private fun of(view: AdminPopupView): AdminPopupResponse =
			AdminPopupResponse(
				id = view.id,
				title = view.title,
				displayOrder = view.displayOrder,
				popUpType = view.popUpType,
				exposedFrom = view.exposedFrom,
				exposedTo = view.exposedTo,
				createdAt = view.createdAt,
			)

		fun listOf(views: AdminPopupViews): List<AdminPopupResponse> =
			views.values.map { view: AdminPopupView -> of(view) }
	}
}
