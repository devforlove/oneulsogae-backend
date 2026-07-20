package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import org.hamcrest.Matchers.greaterThan

/**
 * `GET /users/v1/profile/options` E2E 테스트. (프로필 선택 enum 옵션)
 * 활동지역 목록은 GET /regions/v1로 분리되어 여기엔 포함되지 않는다.
 */
class GetProfileOptionsE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/profile/options") {

		context("호출하면") {
			it("enum 타입별 옵션 목록을 반환한다 (200)") {
				get("/users/v1/profile/options") {
					bearer(accessTokenFor(9001L))
				} expect {
					status(200)
					body("success", true)
					body("data.maritalStatuses.size()", greaterThan(0))
					body("data.bodyTypes", org.hamcrest.Matchers.notNullValue())
				}
			}
		}
	}
})
