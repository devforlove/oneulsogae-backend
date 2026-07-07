package com.org.meeple.admin.report.query.dao

import com.org.meeple.admin.report.query.dto.AdminReportDetailView
import com.org.meeple.admin.report.query.dto.AdminReportSummaryViews

/** 어드민 신고 조회 dao(query out-port). 유저 신고(toUserId 존재)만 다룬다. */
interface GetAdminReportDao {

	/** 유저 신고를 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int): AdminReportSummaryViews

	/** (soft delete 제외) 유저 신고 전체 개수. (페이징 메타데이터 계산용) */
	fun count(): Long

	/** 유저 신고 상세를 id로 조회한다. 없거나 팀 신고면 null. */
	fun findDetailById(id: Long): AdminReportDetailView?
}
