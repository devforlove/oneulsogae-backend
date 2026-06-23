package com.org.meeple.region

import com.org.meeple.infra.region.RegionPoint
import com.org.meeple.infra.region.RegionProximity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class RegionProximityTest : DescribeSpec({

	// 같은 경도에서 위도만 차이 — 위도 0.1도 ≈ 11km, 1.0도 ≈ 111km
	val seoul = RegionPoint(regionId = 1L, latitude = 37.0, longitude = 127.0)
	val near = RegionPoint(regionId = 2L, latitude = 37.1, longitude = 127.0)
	val far = RegionPoint(regionId = 3L, latitude = 38.0, longitude = 127.0)

	describe("nearbyRegionIds") {

		context("기준 지역이 포함돼 있으면") {
			it("자기 지역이 거리 0으로 맨 앞이고, 나머지는 가까운 순으로 정렬된다") {
				val proximity: RegionProximity = RegionProximity.from(listOf(far, seoul, near))

				proximity.nearbyRegionIds(1L) shouldBe listOf(1L, 2L, 3L)
			}
		}

		context("거리가 같은 지역이 둘이면") {
			it("regionId 오름차순으로 안정 정렬한다") {
				val east = RegionPoint(regionId = 5L, latitude = 37.0, longitude = 127.1)
				val west = RegionPoint(regionId = 4L, latitude = 37.0, longitude = 126.9)
				val proximity: RegionProximity = RegionProximity.from(listOf(seoul, east, west))

				// east/west는 seoul에서 거의 같은 거리 → regionId 작은 4가 먼저
				proximity.nearbyRegionIds(1L) shouldBe listOf(1L, 4L, 5L)
			}
		}

		context("모르는 지역이면") {
			it("빈 리스트를 반환한다") {
				val proximity: RegionProximity = RegionProximity.from(listOf(seoul, near))

				proximity.nearbyRegionIds(999L).shouldBeEmpty()
			}
		}
	}
})
