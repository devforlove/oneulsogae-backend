package com.org.oneulsogae.core.region.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.region.RegionErrorCode
import com.org.oneulsogae.core.region.query.dao.GetRegionsDao
import com.org.oneulsogae.core.region.query.dto.RegionView
import com.org.oneulsogae.core.region.query.service.port.`in`.GetRegionUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetRegionUseCase] 구현. (조회 전용)
 * id로 지역을 단건 조회한다. 없으면 [RegionErrorCode.REGION_NOT_FOUND]을 던진다.
 */
@Service
@Transactional(readOnly = true)
class GetRegionService(
	private val getRegionsDao: GetRegionsDao,
) : GetRegionUseCase {

	override fun getById(id: Long): RegionView =
		getRegionsDao.findById(id)
			?: throw BusinessException(RegionErrorCode.REGION_NOT_FOUND, "활동지역을 찾을 수 없습니다: $id")
}
