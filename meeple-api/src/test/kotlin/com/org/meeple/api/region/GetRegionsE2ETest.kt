package com.org.meeple.api.region

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.region.entity.QRegionEntity
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize

/**
 * `GET /regions/v1` E2E 테스트. (활동지역 목록 — id 포함)
 */
class GetRegionsE2ETest : AbstractIntegrationSupport({

	describe("GET /regions/v1") {

		context("활동지역이 적재돼 있으면") {
			it("id를 포함한 전체 지역 목록을 시/도·시/군/구 순으로 반환한다 (200)") {
				IntegrationUtil.persist(RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"))
				IntegrationUtil.persist(RegionEntityFixture.create(sido = "부산광역시", sigungu = "해운대구"))

				get("/regions/v1") {
					bearer(accessTokenFor(9001L))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					body("data.find { it.sigungu == '강남구' }.sido", "서울특별시")
					body("data.find { it.sigungu == '강남구' }.id", greaterThan(0))
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
