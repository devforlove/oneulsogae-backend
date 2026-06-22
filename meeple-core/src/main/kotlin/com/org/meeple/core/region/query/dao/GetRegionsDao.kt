package com.org.meeple.core.region.query.dao

import com.org.meeple.core.region.query.dto.RegionView

/**
 * 지역 목록 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 전체 지역을 시/도·시/군/구 순으로 반환한다.
 */
interface GetRegionsDao {

	fun findAll(): List<RegionView>
}
