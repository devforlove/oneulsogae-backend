package com.org.oneulsogae.core.region.query.service

import com.org.oneulsogae.core.region.query.dao.GetRegionsDao
import com.org.oneulsogae.core.region.query.dto.RegionView
import com.org.oneulsogae.core.region.query.service.port.`in`.GetRegionsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetRegionsUseCase] 구현. (조회 전용)
 * 전체 지역 목록을 dao로 조회한다. (query dao만 의존)
 */
@Service
@Transactional(readOnly = true)
class GetRegionsService(
	private val getRegionsDao: GetRegionsDao,
) : GetRegionsUseCase {

	override fun getAll(): List<RegionView> =
		getRegionsDao.findAll()
}
