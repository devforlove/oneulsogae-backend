package com.org.meeple.core.popup.query.service.port.`in`

import com.org.meeple.core.popup.query.dto.PopupViews

/** 노출 중인 팝업 목록 조회 인포트(유스케이스). */
interface GetPopupsUseCase {

	/** 현재 노출 대상(기간 내)인 팝업을 display_order 오름차순으로 조회한다. (전역 + [userId] 본인 개인 팝업) */
	fun getVisiblePopups(userId: Long): PopupViews
}
