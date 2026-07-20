package com.org.oneulsogae.core.gathering.query.service

import com.org.oneulsogae.common.gathering.GatheringType
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode
import com.org.oneulsogae.core.gathering.query.dao.GetGatheringDao
import com.org.oneulsogae.core.gathering.query.dto.GatheringDetailView
import com.org.oneulsogae.core.gathering.query.dto.GatheringParticipantView
import com.org.oneulsogae.core.gathering.query.dto.GatheringProductIdentity
import com.org.oneulsogae.core.gathering.query.dto.GatheringScheduleView
import com.org.oneulsogae.core.gathering.query.dto.GatheringView
import com.org.oneulsogae.core.gathering.query.dto.GatheringViews
import com.org.oneulsogae.core.gathering.query.dto.GroupedGatherings
import com.org.oneulsogae.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.oneulsogae.core.gathering.query.service.port.out.GatheringImageUrlPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetGatheringsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 목록: 모집중 모임을 최신 등록순으로 조회해 대표 이미지 키(imageKey)를 presigned URL로 변환한 뒤
 * 모임 타입별로 그룹핑한다(타입 3종 항상 포함, 없으면 빈 배열).
 * 상세: 모집중 모임 한 건을 id로 조회하고, 없거나 모집중이 아니면 404를 던진다.
 */
@Service
@Transactional(readOnly = true)
class GetGatheringsService(
	private val getGatheringDao: GetGatheringDao,
	private val gatheringImageUrlPort: GatheringImageUrlPort,
) : GetGatheringsUseCase {

	override fun getGatherings(): GroupedGatherings {
		val rows: GatheringViews = getGatheringDao.findRecruiting()
		val withUrls: GatheringViews = GatheringViews(
			rows.values.map { view: GatheringView ->
				view.copy(imageUrl = presignedUrlOf(view.imageKey))
			},
		)
		return withUrls.groupByType(GatheringType.entries)
	}

	override fun getGathering(id: Long): GatheringDetailView {
		val view: GatheringDetailView = getGatheringDao.findRecruitingDetailById(id)
			?: throw BusinessException(GatheringErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: $id")
		return view.copy(
			imageUrl = presignedUrlOf(view.imageKey),
			schedules = getGatheringDao.findSchedulesByGatheringId(id),
		)
	}

	override fun getGatheringWithParticipants(id: Long): GatheringDetailView {
		val view: GatheringDetailView = getGatheringDao.findRecruitingDetailById(id)
			?: throw BusinessException(GatheringErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: $id")
		val schedules: List<GatheringScheduleView> = getGatheringDao.findSchedulesByGatheringId(id)
		val participantsBySchedule: Map<Long, List<GatheringParticipantView>> = getGatheringDao
			.findParticipantsByScheduleIds(schedules.map { schedule: GatheringScheduleView -> schedule.id })
			.groupBy { participant: GatheringParticipantView -> participant.scheduleId }
		return view.copy(
			imageUrl = presignedUrlOf(view.imageKey),
			schedules = schedules.map { schedule: GatheringScheduleView ->
				schedule.copy(participants = participantsBySchedule[schedule.id] ?: emptyList())
			},
		)
	}

	override fun getProduct(productId: Long): GatheringProductIdentity =
		getGatheringDao.findProductById(productId)
			?: throw BusinessException(GatheringErrorCode.GATHERING_PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

	// 대표 이미지가 없으면 null, 있으면 열람용 presigned URL.
	private fun presignedUrlOf(imageKey: String?): String? =
		imageKey?.let { key: String -> gatheringImageUrlPort.presignedGetUrl(key) }
}
