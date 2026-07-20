package com.org.oneulsogae.admin.gathering.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.query.dao.GetAdminGatheringDao
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringPage
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringView
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringViews
import com.org.oneulsogae.admin.gathering.query.service.port.`in`.GetAdminGatheringsUseCase
import com.org.oneulsogae.admin.gathering.query.service.port.out.GatheringImageUrlPort
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminGatheringsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 모임을 저장 날짜(생성 시각) 최신순으로 limit/offset(page·size) 페이징 조회하고,
 * 각 행의 대표 이미지 키(imageKey)를 presigned 열람 URL(imageUrl)로 변환한다(이미지 없으면 null).
 */
@Service
@Transactional(readOnly = true)
class GetAdminGatheringsService(
	private val getAdminGatheringDao: GetAdminGatheringDao,
	private val gatheringImageUrlPort: GatheringImageUrlPort,
) : GetAdminGatheringsUseCase {

	override fun getGatherings(page: Int, size: Int, status: GatheringStatus?, type: GatheringType?): AdminGatheringPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val rows: AdminGatheringViews = getAdminGatheringDao.findPage(offset, pageSize, status, type)
		val withUrls: List<AdminGatheringView> = rows.values.map { view: AdminGatheringView ->
			view.copy(imageUrl = presignedUrlOf(view.imageKey))
		}
		return AdminGatheringPage(
			content = AdminGatheringViews(withUrls),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminGatheringDao.count(status, type),
		)
	}

	override fun getGathering(id: Long): AdminGatheringDetailView {
		val view: AdminGatheringDetailView = getAdminGatheringDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: $id")
		return view.copy(
			imageUrl = presignedUrlOf(view.imageKey),
			schedules = getAdminGatheringDao.findSchedulesByGatheringId(id),
		)
	}

	// 대표 이미지가 없으면 null, 있으면 열람용 presigned URL.
	private fun presignedUrlOf(imageKey: String?): String? =
		imageKey?.let { gatheringImageUrlPort.presignedGetUrl(it) }

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
