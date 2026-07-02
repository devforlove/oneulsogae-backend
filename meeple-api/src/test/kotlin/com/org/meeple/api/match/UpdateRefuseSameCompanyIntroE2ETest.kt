package com.org.meeple.api.match

import com.org.meeple.api.user.cleanupOnboarding
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.put
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.user.command.entity.UserDetailEntity

/**
 * `PUT /matches/v1/settings/refuse-same-company-intro` E2E 테스트.
 *
 * 같은 회사 구성원 소개 거부 플래그를 변경하는 경로를 검증한다.
 * match_user에 적재된 사용자만 변경할 수 있고(미적재는 400), 변경 결과는 프로필 조회에 반영된다.
 */
class UpdateRefuseSameCompanyIntroE2ETest : AbstractIntegrationSupport({

	describe("PUT /matches/v1/settings/refuse-same-company-intro") {

		context("매칭 가능(match_user 적재) 사용자가 거부를 해제하면") {
			it("성공하고 프로필 조회에 false로 반영된다 (200)") {
				val userId: Long = 6101L
				IntegrationUtil.persist(UserDetailEntity(userId = userId, nickname = "거부해제유저"))
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId))

				put("/matches/v1/settings/refuse-same-company-intro") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"refuseSameCompanyIntro": false}""")
				} expect {
					status(200)
					body("success", true)
				}

				get("/users/v1/profile") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.refuseSameCompanyIntro", false)
				}
			}
		}

		context("거부를 해제했던 사용자가 다시 거부로 바꾸면") {
			it("성공하고 프로필 조회에 true로 반영된다 (200)") {
				val userId: Long = 6102L
				IntegrationUtil.persist(UserDetailEntity(userId = userId, nickname = "재거부유저"))
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, refuseSameCompanyIntro = false))

				put("/matches/v1/settings/refuse-same-company-intro") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"refuseSameCompanyIntro": true}""")
				} expect {
					status(200)
					body("success", true)
				}

				get("/users/v1/profile") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.refuseSameCompanyIntro", true)
				}
			}
		}

		context("match_user에 적재되지 않은(매칭 프로필 미완성) 사용자가 변경하면") {
			it("400을 반환한다") {
				val userId: Long = 6103L
				IntegrationUtil.persist(UserDetailEntity(userId = userId, nickname = "미적재유저"))

				put("/matches/v1/settings/refuse-same-company-intro") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"refuseSameCompanyIntro": false}""")
				} expect {
					status(400)
				}
			}
		}

		context("플래그 필드를 누락하면") {
			it("400을 반환한다") {
				val userId: Long = 6104L
				IntegrationUtil.persist(UserDetailEntity(userId = userId, nickname = "누락유저"))
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId))

				put("/matches/v1/settings/refuse-same-company-intro") {
					bearer(accessTokenFor(userId))
					jsonBody("""{}""")
				} expect {
					status(400)
				}
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})
