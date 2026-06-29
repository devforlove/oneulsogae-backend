package com.org.meeple.core.fixture

import com.org.meeple.common.popup.PopupType
import com.org.meeple.core.popup.command.domain.Popup
import java.time.LocalDateTime

/** [Popup] 도메인 모델 테스트 픽스처. 기본은 기간 제한 없는 팝업이다. */
object PopupFixture {

	fun create(
		id: Long = 0,
		title: String? = "이벤트",
		description: String? = "설명",
		displayOrder: Int = 1,
		imageCode: String? = null,
		linkUrl: String? = null,
		buttonText: String? = null,
		popUpType: PopupType = PopupType.NORMAL,
		userId: Long? = null,
		exposedFrom: LocalDateTime = Popup.EXPOSED_FROM_MIN,
		exposedTo: LocalDateTime = Popup.EXPOSED_TO_MAX,
	): Popup =
		Popup(
			id = id,
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
}
