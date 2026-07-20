package com.org.oneulsogae.admin.inquiry.query.dao

import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryViews
import com.org.oneulsogae.common.inquiry.InquiryStatus

/** 어드민 문의 조회 dao(query out-port). [status]가 null이면 전체, 있으면 해당 상태만. */
interface GetAdminInquiryDao {

	/** 문의를 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int, status: InquiryStatus?): AdminInquiryViews

	/** (필터 적용·soft delete 제외) 전체 문의 개수. (페이징 메타데이터 계산용) */
	fun count(status: InquiryStatus?): Long

	/** 문의 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminInquiryDetailView?
}
