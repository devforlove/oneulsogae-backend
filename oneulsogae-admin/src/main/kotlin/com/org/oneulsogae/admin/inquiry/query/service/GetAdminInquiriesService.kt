package com.org.oneulsogae.admin.inquiry.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.inquiry.query.dao.GetAdminInquiryDao
import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryPage
import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryViews
import com.org.oneulsogae.admin.inquiry.query.service.port.`in`.GetAdminInquiriesUseCase
import com.org.oneulsogae.common.inquiry.InquiryStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminInquiriesUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 문의를 접수 시각(created_at) 최신순으로 limit/offset(page·size) 페이징 조회하고,
 * 전체 개수를 함께 조회해 페이지 메타데이터([AdminInquiryPage])를 구성한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminInquiriesService(
	private val getAdminInquiryDao: GetAdminInquiryDao,
) : GetAdminInquiriesUseCase {

	override fun getInquiries(page: Int, size: Int, status: InquiryStatus?): AdminInquiryPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val inquiries: AdminInquiryViews = getAdminInquiryDao.findPage(offset, pageSize, status)
		return AdminInquiryPage(
			content = inquiries,
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminInquiryDao.count(status),
		)
	}

	override fun getInquiry(id: Long): AdminInquiryDetailView =
		getAdminInquiryDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.INQUIRY_NOT_FOUND, "문의를 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
