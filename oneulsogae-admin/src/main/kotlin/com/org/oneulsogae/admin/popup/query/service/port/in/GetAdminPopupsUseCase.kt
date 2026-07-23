package com.org.oneulsogae.admin.popup.query.service.port.`in`

import com.org.oneulsogae.admin.popup.query.dto.AdminPopupDetailView
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupPage

/** 어드민 팝업 조회 유스케이스. (조회 전용 — 전역 팝업만) */
interface GetAdminPopupsUseCase {

	/** 전역 팝업을 노출 순서로 page(0부터)·size 페이징 조회한다. */
	fun getPopups(page: Int, size: Int): AdminPopupPage

	/** 전역 팝업 상세를 id로 조회한다. 없으면 POPUP_NOT_FOUND. */
	fun getPopup(id: Long): AdminPopupDetailView
}
