package com.org.meeple.core.fixture

import com.org.meeple.core.popup.domain.Popup
import java.time.LocalDateTime

/** [Popup] 도메인 모델 테스트 픽스처. 기본은 노출 ON·기간 제한 없는 팝업이다. */
object PopupFixture {

	fun create(
		id: Long = 0,
		title: String = "이벤트",
		description: String = "설명",
		displayOrder: Int = 1,
		imageUrl: String? = null,
		linkUrl: String? = null,
		exposed: Boolean = true,
		exposedFrom: LocalDateTime? = null,
		exposedTo: LocalDateTime? = null,
	): Popup =
		Popup(
			id = id,
			title = title,
			description = description,
			displayOrder = displayOrder,
			imageUrl = imageUrl,
			linkUrl = linkUrl,
			exposed = exposed,
			exposedFrom = exposedFrom,
			exposedTo = exposedTo,
		)
}
