package com.org.oneulsogae.admin.popup.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.popup.query.dao.GetAdminPopupDao
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupDetailView
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupPage
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupViews
import com.org.oneulsogae.admin.popup.query.service.port.`in`.GetAdminPopupsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminPopupsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 전역 팝업을 노출 순서(display_order asc)로 limit/offset(page·size) 페이징 조회하고,
 * 전체 개수를 함께 조회해 페이지 메타데이터([AdminPopupPage])를 구성한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminPopupsService(
	private val getAdminPopupDao: GetAdminPopupDao,
) : GetAdminPopupsUseCase {

	override fun getPopups(page: Int, size: Int): AdminPopupPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val popups: AdminPopupViews = getAdminPopupDao.findPage(offset, pageSize)
		return AdminPopupPage(
			content = popups,
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminPopupDao.count(),
		)
	}

	override fun getPopup(id: Long): AdminPopupDetailView =
		getAdminPopupDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.POPUP_NOT_FOUND, "팝업을 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
