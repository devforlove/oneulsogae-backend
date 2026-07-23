package com.org.oneulsogae.admin.popup.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/**
 * 어드민이 관리하는 전역 팝업 도메인 모델(명령 측).
 * 개인 팝업(환불 안내 등, user_id 보유)은 시스템이 생성하므로 어드민 관리 대상이 아니다 — 이 모델은 전역 팝업만 다룬다.
 * (admin은 core에 의존하지 않으므로 core Popup을 쓰지 않고 자체 모델을 둔다. 노출 기간 센티넬 값은 core와 동일하게 맞춘다)
 * 입력 길이 검증은 요청 DTO에서, 도메인 규칙(기간 순서·생성 가능 유형)은 [validate]에서 처리한다.
 */
data class AdminPopup(
	val id: Long = 0,
	val title: String? = null,
	val description: String? = null,
	val displayOrder: Int,
	val imageCode: String? = null,
	val linkUrl: String? = null,
	val buttonText: String? = null,
	val popUpType: PopupType,
	val exposedFrom: LocalDateTime,
	val exposedTo: LocalDateTime,
) {

	companion object {

		/** 노출 시작 제한 없음 센티넬(아주 과거). core Popup.EXPOSED_FROM_MIN과 같은 값이어야 한다. */
		val EXPOSED_FROM_MIN: LocalDateTime = LocalDateTime.of(1000, 1, 1, 0, 0)

		/** 노출 종료 제한 없음 센티넬(아주 미래). core Popup.EXPOSED_TO_MAX와 같은 값이어야 한다. */
		val EXPOSED_TO_MAX: LocalDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59)

		/**
		 * 어드민 팝업을 만든다. [id]가 0이면 신규, 0이 아니면 기존 팝업의 전체 교체본이다.
		 * 노출 기간을 주지 않으면 제한 없음(센티넬)으로 둔다.
		 * - 노출 종료가 시작보다 앞서면 [AdminErrorCode.POPUP_INVALID_EXPOSURE_PERIOD]
		 * - 1회 조회 후 제거되는 유형([PopupType.removeAfterView])은 전역 팝업으로 만들 수 없다: [AdminErrorCode.POPUP_INVALID_TYPE]
		 *   (한 사용자가 조회하는 순간 soft-delete되어 모두에게서 사라진다)
		 */
		fun create(
			id: Long = 0,
			title: String?,
			description: String?,
			displayOrder: Int,
			imageCode: String?,
			linkUrl: String?,
			buttonText: String?,
			popUpType: PopupType,
			exposedFrom: LocalDateTime?,
			exposedTo: LocalDateTime?,
		): AdminPopup {
			val from: LocalDateTime = exposedFrom ?: EXPOSED_FROM_MIN
			val to: LocalDateTime = exposedTo ?: EXPOSED_TO_MAX
			validate(popUpType, from, to)
			return AdminPopup(
				id = id,
				title = title,
				description = description,
				displayOrder = displayOrder,
				imageCode = imageCode,
				linkUrl = linkUrl,
				buttonText = buttonText,
				popUpType = popUpType,
				exposedFrom = from,
				exposedTo = to,
			)
		}

		private fun validate(popUpType: PopupType, exposedFrom: LocalDateTime, exposedTo: LocalDateTime) {
			if (exposedTo.isBefore(exposedFrom)) {
				throw AdminException(AdminErrorCode.POPUP_INVALID_EXPOSURE_PERIOD)
			}
			if (popUpType.removeAfterView) {
				throw AdminException(AdminErrorCode.POPUP_INVALID_TYPE)
			}
		}
	}
}
