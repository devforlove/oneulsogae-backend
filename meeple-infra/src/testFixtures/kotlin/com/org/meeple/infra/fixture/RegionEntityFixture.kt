package com.org.meeple.infra.fixture

import com.org.meeple.infra.region.entity.RegionEntity

/**
 * [RegionEntity] 테스트 픽스처. 시/도 + 시/군/구 + 좌표를 합리적 기본값으로 채운다.
 */
object RegionEntityFixture {

	fun create(
		sido: String = "서울특별시",
		sigungu: String = "강남구",
		longitude: Double = 127.0473,
		latitude: Double = 37.5172,
		order: Int = 0,
	): RegionEntity =
		RegionEntity(
			sido = sido,
			sigungu = sigungu,
			longitude = longitude,
			latitude = latitude,
			order = order,
		)
}
