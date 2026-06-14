package com.org.meeple.core.popup.domain

import java.time.LocalDateTime

/**
 * 앱에 노출하는 팝업(공지/이벤트 등) 도메인 모델.
 * [exposed]가 true이고 노출 기간([exposedFrom], [exposedTo]) 안일 때 노출 대상이며, [displayOrder] 오름차순으로 보여준다.
 * 영속성은 [com.org.meeple.infra.popup.entity.PopupEntity]가 담당한다.
 */
data class Popup(
	val id: Long = 0,
	val title: String,
	val description: String,
	val displayOrder: Int,
	val imageUrl: String? = null,
	val linkUrl: String? = null,
	val exposed: Boolean = true,
	val exposedFrom: LocalDateTime? = null,
	val exposedTo: LocalDateTime? = null,
) {

	/**
	 * [now] 기준으로 지금 노출 대상인지 여부.
	 * [exposed]가 true이고, 노출 시작 전이 아니며, 노출 종료 후도 아닐 때 true. (기간이 null이면 그쪽 제한 없음)
	 */
	fun isVisible(now: LocalDateTime): Boolean {
		if (!exposed) return false
		if (exposedFrom != null && now.isBefore(exposedFrom)) return false
		if (exposedTo != null && now.isAfter(exposedTo)) return false
		return true
	}
}
