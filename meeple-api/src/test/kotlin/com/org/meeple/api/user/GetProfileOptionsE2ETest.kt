package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.region.entity.QRegionEntity
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize

/**
 * `GET /users/v1/profile/options` E2E 테스트. (프로필 선택 옵션 + 활동지역 전체 목록)
 * enum 옵션과 함께 regions 테이블의 전체 지역(시/도+시/군/구)을 내려준다.
 */
class GetProfileOptionsE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/profile/options") {

		context("활동지역이 적재돼 있으면") {
			it("enum 옵션과 함께 전체 지역 목록을 반환한다 (200)") {
				IntegrationUtil.persist(RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"))
				IntegrationUtil.persist(RegionEntityFixture.create(sido = "부산광역시", sigungu = "해운대구"))

				get("/users/v1/profile/options") {
					bearer(accessTokenFor(9001L))
				} expect {
					status(200)
					body("success", true)
					// enum 옵션은 그대로 내려온다.
					body("data.maritalStatuses.size()", greaterThan(0))
					// 활동지역 전체 목록. (id 포함)
					body("data.regions", hasSize<Any>(2))
					body("data.regions.find { it.sigungu == '강남구' }.sido", "서울특별시")
					body("data.regions.find { it.sigungu == '강남구' }.id", greaterThan(0))
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
