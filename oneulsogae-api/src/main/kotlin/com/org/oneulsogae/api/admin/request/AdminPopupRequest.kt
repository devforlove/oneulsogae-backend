package com.org.oneulsogae.api.admin.request

import com.org.oneulsogae.admin.popup.command.application.port.`in`.command.AdminPopupCommand
import com.org.oneulsogae.common.popup.PopupType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 어드민 팝업 생성/수정 요청. 생성과 수정이 같은 전체 필드를 받아 한 요청 DTO를 공유한다.
 * 길이 제한은 popups 테이블 컬럼 길이와 맞춘다. 기간 순서·생성 가능 유형 검증은 admin 도메인(AdminPopup)이 한다.
 */
data class AdminPopupRequest(
	@field:Size(max = 200, message = "팝업 제목은 200자 이하여야 합니다.")
	val title: String? = null,

	@field:Size(max = 1000, message = "팝업 설명은 1000자 이하여야 합니다.")
	val description: String? = null,

	@field:NotNull(message = "노출 순서는 필수입니다.")
	@field:PositiveOrZero(message = "노출 순서는 0 이상이어야 합니다.")
	val displayOrder: Int? = null,

	@field:Size(max = 100, message = "이미지 코드는 100자 이하여야 합니다.")
	val imageCode: String? = null,

	@field:Size(max = 500, message = "링크 URL은 500자 이하여야 합니다.")
	val linkUrl: String? = null,

	@field:Size(max = 100, message = "버튼 문구는 100자 이하여야 합니다.")
	val buttonText: String? = null,

	/** 팝업 유형. 생략하면 NORMAL. */
	val popUpType: PopupType? = null,

	/** 노출 시작 시각. 생략하면 제한 없음. */
	val exposedFrom: LocalDateTime? = null,

	/** 노출 종료 시각. 생략하면 제한 없음. */
	val exposedTo: LocalDateTime? = null,
) {
	// @Valid 통과 후 호출 → displayOrder non-null 보장 → command로 변환
	fun toCommand(): AdminPopupCommand =
		AdminPopupCommand(
			title = title,
			description = description,
			displayOrder = displayOrder!!,
			imageCode = imageCode,
			linkUrl = linkUrl,
			buttonText = buttonText,
			popUpType = popUpType ?: PopupType.NORMAL,
			exposedFrom = exposedFrom,
			exposedTo = exposedTo,
		)
}
