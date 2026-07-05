package com.org.meeple.admin.gathering.query.service

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.gathering.query.dao.GetAdminGatheringDao
import com.org.meeple.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringPage
import com.org.meeple.admin.gathering.query.dto.AdminGatheringViews
import com.org.meeple.admin.gathering.query.service.port.`in`.GetAdminGatheringsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminGatheringsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 모임을 저장 날짜(생성 시각) 최신순으로 limit/offset(page·size) 페이징 조회하고,
 * 전체 개수를 함께 조회해 페이지 메타데이터([AdminGatheringPage])를 구성한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminGatheringsService(
	private val getAdminGatheringDao: GetAdminGatheringDao,
) : GetAdminGatheringsUseCase {

	override fun getGatherings(page: Int, size: Int): AdminGatheringPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val gatherings: AdminGatheringViews = getAdminGatheringDao.findPage(offset, pageSize)
		return AdminGatheringPage(
			content = gatherings,
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminGatheringDao.count(),
		)
	}

	override fun getGathering(id: Long): AdminGatheringDetailView =
		getAdminGatheringDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
