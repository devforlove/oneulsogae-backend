package com.org.meeple.infra.region.query

import com.org.meeple.core.region.query.dao.GetRegionsDao
import com.org.meeple.core.region.query.dto.RegionView
import com.org.meeple.infra.region.entity.QRegionEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetRegionsDao]의 QueryDSL 구현체. (조회 전용)
 * regions 테이블 전체를 시/도·시/군/구 순으로 [RegionView]로 투영한다.
 */
@Component
class GetRegionsDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetRegionsDao {

	override fun findAll(): List<RegionView> {
		val region: QRegionEntity = QRegionEntity.regionEntity
		return queryFactory
			.select(Projections.constructor(RegionView::class.java, region.id, region.sido, region.sigungu))
			.from(region)
			.orderBy(region.sido.asc(), region.sigungu.asc())
			.fetch()
	}

	override fun findById(id: Long): RegionView? {
		val region: QRegionEntity = QRegionEntity.regionEntity
		return queryFactory
			.select(Projections.constructor(RegionView::class.java, region.id, region.sido, region.sigungu))
			.from(region)
			.where(region.id.eq(id))
			.fetchOne()
	}
}
