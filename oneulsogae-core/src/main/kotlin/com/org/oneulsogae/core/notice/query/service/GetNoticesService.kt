package com.org.oneulsogae.core.notice.query.service

import com.org.oneulsogae.core.notice.query.dao.GetNoticeDao
import com.org.oneulsogae.core.notice.query.dto.NoticePage
import com.org.oneulsogae.core.notice.query.dto.NoticeViews
import com.org.oneulsogae.core.notice.query.service.port.`in`.GetNoticesUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetNoticesUseCase] 구현. (조회 전용 - 쓰기 부수효과를 두지 않는다)
 * 공지를 저장 날짜(생성 시각) 최신순으로 limit/offset(page·size) 페이징해 조회한다.
 * 전체 개수를 함께 조회해 페이지 메타데이터([NoticePage])를 구성한다.
 */
@Service
@Transactional(readOnly = true)
class GetNoticesService(
	private val getNoticeDao: GetNoticeDao,
) : GetNoticesUseCase {

	override fun getNotices(page: Int, size: Int): NoticePage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val notices: NoticeViews = getNoticeDao.findPage(offset, pageSize)
		return NoticePage(
			notices = notices,
			page = pageNumber,
			size = pageSize,
			totalElements = getNoticeDao.count(),
		)
	}

	companion object {
		/** 한 페이지 최대 조회 건수. 과도한 size 요청을 막는다. */
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
