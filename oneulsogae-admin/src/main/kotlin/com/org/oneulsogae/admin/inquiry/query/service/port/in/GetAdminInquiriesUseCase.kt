package com.org.oneulsogae.admin.inquiry.query.service.port.`in`

import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryPage
import com.org.oneulsogae.common.inquiry.InquiryStatus

/** 어드민 문의 조회 유스케이스. (조회 전용) */
interface GetAdminInquiriesUseCase {

	/** 문의를 최신순으로 page(0부터)·size 페이징 조회한다. [status]가 null이면 전체. */
	fun getInquiries(page: Int, size: Int, status: InquiryStatus?): AdminInquiryPage

	/** 문의 상세를 id로 조회한다. 없으면 INQUIRY_NOT_FOUND. */
	fun getInquiry(id: Long): AdminInquiryDetailView
}
