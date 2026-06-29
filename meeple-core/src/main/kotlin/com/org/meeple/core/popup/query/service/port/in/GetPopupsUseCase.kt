package com.org.meeple.core.popup.query.service.port.`in`

import com.org.meeple.core.popup.query.dto.PopupViews

/** 노출 중인 팝업 목록 조회 인포트(유스케이스). */
interface GetPopupsUseCase {

	/**
	 * 현재 노출 대상(기간 내)인 팝업을 display_order 오름차순으로 조회한다. (전역 + [userId] 본인 개인 팝업)
	 * [isNewUser]가 true면 신규 유저(NEW_USER) 팝업도 함께 노출하고, false면 제외한다.
	 */
	fun getVisiblePopups(userId: Long, isNewUser: Boolean): PopupViews
}
