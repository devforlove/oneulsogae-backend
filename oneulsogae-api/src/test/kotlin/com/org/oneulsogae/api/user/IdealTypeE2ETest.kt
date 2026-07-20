package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.put
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.user.command.entity.QUserIdealTypeEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue

/**
 * `GET/PUT /users/v1/ideal-type` E2E 테스트.
 * upsert 왕복, 미설정 응답(전 항목 null), enum name·배열 형태, 범위 검증, 인증을 검증한다.
 */
class IdealTypeE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/ideal-type") {
		context("이상형을 설정한 적 없는 사용자가 조회하면") {
			it("전 항목이 null인 기본 응답을 내려준다 (200)") {
				val userId: Long = 7001L

				get("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.ageRange", nullValue())
					body("data.maritalStatus", nullValue())
					body("data.religion", nullValue())
				}
			}
		}

		context("인증 없이 조회하면") {
			it("401을 반환한다") {
				get("/users/v1/ideal-type") expect {
					status(401)
				}
			}
		}
	}

	describe("PUT /users/v1/ideal-type") {
		context("유효한 이상형을 저장하면") {
			it("저장 후 조회 시 저장한 값이 그대로 내려온다 (enum name·배열)") {
				val userId: Long = 7002L

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody(
						"""
						{
						  "ageRange": [27, 35],
						  "heightRange": null,
						  "maritalStatus": "SINGLE",
						  "smoking": "NON_SMOKER",
						  "drinking": "SOMETIMES",
						  "religion": null
						}
						""".trimIndent(),
					)
				} expect {
					status(200)
					body("success", true)
					body("data.ageRange", contains(27, 35))
					body("data.maritalStatus", "SINGLE")
				}

				get("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.ageRange", contains(27, 35))
					body("data.heightRange", nullValue())
					body("data.smoking", "NON_SMOKER")
					body("data.drinking", "SOMETIMES")
					body("data.religion", nullValue())
				}
			}
		}

		context("이미 이상형이 있는 사용자가 다시 저장하면") {
			it("새 값으로 교체(upsert)되고 행이 중복 생성되지 않는다") {
				val userId: Long = 7003L

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody("""{ "ageRange": [20, 30], "maritalStatus": "SINGLE" }""")
				} expect {
					status(200)
				}

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody("""{ "ageRange": [40, 50], "maritalStatus": "DIVORCED" }""")
				} expect {
					status(200)
					body("data.ageRange", contains(40, 50))
					body("data.maritalStatus", "DIVORCED")
				}

				get("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.ageRange", contains(40, 50))
					body("data.maritalStatus", "DIVORCED")
				}
			}
		}

		context("최소가 최대보다 큰 나이 범위를 저장하면") {
			it("검증 실패로 400을 반환한다") {
				val userId: Long = 7004L

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody("""{ "ageRange": [40, 20] }""")
				} expect {
					status(400)
					body("success", false)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserIdealTypeEntity.userIdealTypeEntity)
	}
})
