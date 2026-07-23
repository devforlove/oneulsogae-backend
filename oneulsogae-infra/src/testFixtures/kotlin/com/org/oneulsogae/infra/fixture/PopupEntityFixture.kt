package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.infra.popup.command.entity.PopupEntity
import java.time.LocalDateTime

/** [PopupEntity] 테스트 픽스처. 기본은 노출 기간 제한이 없는 전역(NORMAL) 팝업이다. */
object PopupEntityFixture {

	fun create(
		title: String? = "팝업",
		description: String? = null,
		displayOrder: Int = 0,
		imageCode: String? = null,
		linkUrl: String? = null,
		buttonText: String? = null,
		popUpType: PopupType = PopupType.NORMAL,
		userId: Long? = null,
		exposedFrom: LocalDateTime = LocalDateTime.of(1000, 1, 1, 0, 0),
		exposedTo: LocalDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59),
	): PopupEntity =
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
		)
}
