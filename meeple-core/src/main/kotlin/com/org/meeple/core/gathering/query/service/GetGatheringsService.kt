package com.org.meeple.core.gathering.query.service

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.query.dao.GetGatheringDao
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GatheringViews
import com.org.meeple.core.gathering.query.dto.GroupedGatherings
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.meeple.core.gathering.query.service.port.out.GatheringImageUrlPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetGatheringsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 모집중 모임을 gatheringAt 임박순으로 조회하고, 각 행의 대표 이미지 키(imageKey)를
 * presigned 열람 URL(imageUrl)로 변환한 뒤(이미지 없으면 null), 모임 타입별로 그룹핑한다.
 * (타입 3종을 항상 모두 포함하고, 해당 타입 모임이 없으면 빈 배열)
 */
@Service
@Transactional(readOnly = true)
class GetGatheringsService(
	private val getGatheringDao: GetGatheringDao,
	private val gatheringImageUrlPort: GatheringImageUrlPort,
) : GetGatheringsUseCase {

	override fun getGatherings(): GroupedGatherings {
		val rows: GatheringViews = getGatheringDao.findRecruitingOrderByGatheringAt()
		val withUrls: GatheringViews = GatheringViews(
			rows.values.map { view: GatheringView ->
				view.copy(imageUrl = presignedUrlOf(view.imageKey))
			},
		)
		return withUrls.groupByType(GatheringType.entries)
	}

	// 대표 이미지가 없으면 null, 있으면 열람용 presigned URL.
	private fun presignedUrlOf(imageKey: String?): String? =
		imageKey?.let { key: String -> gatheringImageUrlPort.presignedGetUrl(key) }
}
