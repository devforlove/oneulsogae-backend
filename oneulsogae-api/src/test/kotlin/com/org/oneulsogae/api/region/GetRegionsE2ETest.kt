package com.org.oneulsogae.api.region

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize

/**
 * `GET /regions/v1` E2E 테스트. (활동지역 목록 — id 포함)
 */
class GetRegionsE2ETest : AbstractIntegrationSupport({

	describe("GET /regions/v1/list") {

		context("활동지역이 적재돼 있으면") {
			it("id를 포함한 전체 지역 목록을 order 오름차순으로 반환한다 (200)") {
				// 적재 순서와 무관하게 order 오름차순(해운대=1 → 강남=2)으로 정렬돼야 한다.
				IntegrationUtil.persist(RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", order = 2))
				IntegrationUtil.persist(RegionEntityFixture.create(sido = "부산광역시", sigungu = "해운대구", order = 1))

				get("/regions/v1/list") {
					bearer(accessTokenFor(9001L))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					// order 오름차순 → [0]=해운대구(1), [1]=강남구(2)
					body("data[0].sigungu", "해운대구")
					body("data[1].sigungu", "강남구")
					body("data[0].id", greaterThan(0))
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
