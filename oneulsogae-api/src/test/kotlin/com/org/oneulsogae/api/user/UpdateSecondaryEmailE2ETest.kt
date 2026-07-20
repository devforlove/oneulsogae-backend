package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.put
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import org.hamcrest.Matchers.nullValue

/**
 * `PUT /users/v1/profile/secondary-email` E2E 테스트.
 *
 * 마케팅·광고·매칭 알림 수신용 보조 이메일을 설정/변경/해제하는 경로를 검증한다.
 */
class UpdateSecondaryEmailE2ETest : AbstractIntegrationSupport({

	describe("PUT /users/v1/profile/secondary-email") {

		context("보조 이메일을 설정하면") {
			it("응답과 조회 모두에 보조 이메일이 내려온다 (200)") {
				val userId: Long = 6001L
				IntegrationUtil.persist(UserDetailEntity(userId = userId, nickname = "보조유저"))

				put("/users/v1/profile/secondary-email") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"secondaryEmail": "marketing@user.com"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.secondaryEmail", "marketing@user.com")
				}

				get("/users/v1/profile") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.secondaryEmail", "marketing@user.com")
				}
			}
		}

		context("이미 설정된 보조 이메일에 null을 주면") {
			it("보조 이메일이 해제된다 (200)") {
				val userId: Long = 6002L
				IntegrationUtil.persist(
					UserDetailEntity(userId = userId, nickname = "해제유저", secondaryEmail = "old@user.com"),
				)

				put("/users/v1/profile/secondary-email") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"secondaryEmail": null}""")
				} expect {
					status(200)
					body("data.secondaryEmail", nullValue())
				}
			}
		}

		context("이메일 형식이 올바르지 않으면") {
			it("400을 반환한다") {
				val userId: Long = 6003L
				IntegrationUtil.persist(UserDetailEntity(userId = userId, nickname = "형식유저"))

				put("/users/v1/profile/secondary-email") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"secondaryEmail": "not-an-email"}""")
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
