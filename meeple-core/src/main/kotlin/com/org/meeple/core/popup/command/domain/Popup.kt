package com.org.meeple.core.popup.command.domain

import com.org.meeple.common.popup.PopupType
import java.time.LocalDateTime

/**
 * 앱에 노출하는 팝업(공지/이벤트 등) 도메인 모델. (명령 측 — 생성/저장에 쓴다)
 * 노출 기간([exposedFrom], [exposedTo]) 안일 때 노출 대상이며, [displayOrder] 오름차순으로 보여준다.
 * 노출 끄기(숨김)는 별도 플래그 없이 soft delete(deleted_at)로 처리한다. (삭제된 팝업은 조회에서 제외된다)
 * [userId]가 null이면 전역 팝업(모든 사용자에게 노출), 값이 있으면 그 사용자에게만 노출하는 개인 팝업(예: 환불 안내)이다.
 * 조회는 도메인 모델 대신 query 측 read model([com.org.meeple.core.popup.query.dto.PopupView])을 쓴다.
 * 영속성은 [com.org.meeple.infra.popup.command.entity.PopupEntity]가 담당한다.
 */
data class Popup(
	val id: Long = 0,
	val title: String? = null,
	val description: String? = null,
	val displayOrder: Int,
	val imageCode: String? = null,
	val linkUrl: String? = null,
	val buttonText: String? = null,
	val popUpType: PopupType = PopupType.NORMAL,
	val userId: Long? = null,
	val exposedFrom: LocalDateTime = EXPOSED_FROM_MIN,
	val exposedTo: LocalDateTime = EXPOSED_TO_MAX,
) {

	/**
	 * [now] 기준으로 지금 노출 기간 안인지 여부.
	 * 노출 시작 전이 아니고 노출 종료 후도 아닐 때 true. (기간 제한이 없으면 [EXPOSED_FROM_MIN]/[EXPOSED_TO_MAX] 센티넬이라 항상 기간 안)
	 * 숨김 처리는 soft delete로 하므로 여기서 따로 보지 않는다.
	 */
	fun isVisible(now: LocalDateTime): Boolean {
		if (now.isBefore(exposedFrom)) return false
		if (now.isAfter(exposedTo)) return false
		return true
	}

	companion object {

		/** 노출 시작 제한 없음을 나타내는 센티넬(아주 과거). 기간 비교를 nullable 대신 순수 레인지로 만들기 위해 쓴다. */
		val EXPOSED_FROM_MIN: LocalDateTime = LocalDateTime.of(1000, 1, 1, 0, 0)

		/** 노출 종료 제한 없음을 나타내는 센티넬(아주 미래). */
		val EXPOSED_TO_MAX: LocalDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59)

		/** 개인 환불 팝업의 노출 기간(일). 환불 안내가 무한히 남지 않도록 생성 시점부터 이 기간까지만 노출한다. */
		private const val REFUND_POPUP_EXPOSURE_DAYS: Long = 1000L

		/** 소개팅 매칭 실패 환불 팝업 이미지의 image_templates 코드. (실제 이미지는 DB에서 교체) */
		const val MATCH_FAILED_REFUND_IMAGE_CODE: String = "POPUP_MATCH_FAILED_REFUND"

		/** 미팅(팀) 매칭 실패 환불 팝업 이미지의 image_templates 코드. */
		const val MEETING_FAILED_REFUND_IMAGE_CODE: String = "POPUP_MEETING_FAILED_REFUND"

		/**
		 * 소개팅 매칭 실패로 [refundAmount]코인을 환불한 사실을 알리는 개인([userId]) 팝업을 만든다.
		 * [now]부터 [REFUND_POPUP_EXPOSURE_DAYS]일 동안만 노출한다. (별도 읽음 처리 개념이 없어 노출 기간으로 정리한다)
		 */
		fun matchFailedRefund(userId: Long, refundAmount: Int, now: LocalDateTime): Popup =
			Popup(
				title = "소개팅 매칭 실패 환불",
				description = "소개팅이 매칭되지 않아 사용한 코인의 절반인 ${refundAmount}코인을 돌려드렸어요.",
				displayOrder = 0,
				imageCode = MATCH_FAILED_REFUND_IMAGE_CODE,
				buttonText = "확인",
				popUpType = PopupType.MATCH_FAILED_REFUND,
				userId = userId,
				exposedFrom = now,
				exposedTo = now.plusDays(REFUND_POPUP_EXPOSURE_DAYS),
			)

		/**
		 * 미팅(팀) 매칭 실패로 [refundAmount]코인을 환불한 사실을 알리는 개인([userId]) 팝업을 만든다.
		 * [now]부터 [REFUND_POPUP_EXPOSURE_DAYS]일 동안만 노출한다. (소개팅 [matchFailedRefund]와 동일 골격, 문구만 미팅 기준)
		 */
		fun meetingFailedRefund(userId: Long, refundAmount: Int, now: LocalDateTime): Popup =
			Popup(
				title = "미팅 매칭 실패 환불",
				description = "미팅이 매칭되지 않아 사용한 코인의 절반인 ${refundAmount}코인을 돌려드렸어요.",
				displayOrder = 0,
				imageCode = MEETING_FAILED_REFUND_IMAGE_CODE,
				buttonText = "확인",
				popUpType = PopupType.MEETING_FAILED_REFUND,
				userId = userId,
				exposedFrom = now,
				exposedTo = now.plusDays(REFUND_POPUP_EXPOSURE_DAYS),
			)
	}
}
