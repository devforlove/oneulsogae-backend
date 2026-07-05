package com.org.meeple.admin.notice.query.dao

import com.org.meeple.admin.notice.query.dto.AdminNoticeDetailView
import com.org.meeple.admin.notice.query.dto.AdminNoticeViews

/** 어드민 공지 조회 dao(query out-port). */
interface GetAdminNoticeDao {

	/** 공지를 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int): AdminNoticeViews

	/** (soft delete 제외) 전체 공지 개수. (페이징 메타데이터 계산용) */
	fun count(): Long

	/** 공지 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminNoticeDetailView?
}
