package com.org.oneulsogae.admin.popup.query.dao

import com.org.oneulsogae.admin.popup.query.dto.AdminPopupDetailView
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupViews

/**
 * 어드민 팝업 조회 dao(query out-port).
 * 어드민 관리 대상은 전역 팝업뿐이라 모든 조회에서 개인 팝업(user_id 보유)을 제외한다.
 */
interface GetAdminPopupDao {

	/** 전역 팝업을 노출 순서(display_order asc, 동률이면 id desc)로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int): AdminPopupViews

	/** (soft delete·개인 팝업 제외) 전역 팝업 전체 개수. (페이징 메타데이터 계산용) */
	fun count(): Long

	/** 전역 팝업 상세를 [id]로 조회한다. 없거나 soft-delete·개인 팝업이면 null. */
	fun findDetailById(id: Long): AdminPopupDetailView?
}
