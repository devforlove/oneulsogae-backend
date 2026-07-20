package com.org.oneulsogae.admin.notice.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.notice.query.dao.GetAdminNoticeDao
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeDetailView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticePage
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeViews
import com.org.oneulsogae.admin.notice.query.service.port.`in`.GetAdminNoticesUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminNoticesUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 공지를 저장 날짜(생성 시각) 최신순으로 limit/offset(page·size) 페이징 조회하고,
 * 전체 개수를 함께 조회해 페이지 메타데이터([AdminNoticePage])를 구성한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminNoticesService(
	private val getAdminNoticeDao: GetAdminNoticeDao,
) : GetAdminNoticesUseCase {

	override fun getNotices(page: Int, size: Int): AdminNoticePage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val notices: AdminNoticeViews = getAdminNoticeDao.findPage(offset, pageSize)
		return AdminNoticePage(
			content = notices,
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminNoticeDao.count(),
		)
	}

	override fun getNotice(id: Long): AdminNoticeDetailView =
		getAdminNoticeDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.NOTICE_NOT_FOUND, "공지를 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
