package com.org.oneulsogae.admin.notice.query.service.port.`in`

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeDetailView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticePage

/** 어드민 공지 조회 유스케이스. (조회 전용) */
interface GetAdminNoticesUseCase {

	/** 공지를 최신순으로 page(0부터)·size 페이징 조회한다. */
	fun getNotices(page: Int, size: Int): AdminNoticePage

	/** 공지 상세를 id로 조회한다. 없으면 NOTICE_NOT_FOUND. */
	fun getNotice(id: Long): AdminNoticeDetailView
}
