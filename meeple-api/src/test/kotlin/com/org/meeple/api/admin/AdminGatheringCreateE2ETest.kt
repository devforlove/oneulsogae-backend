package com.org.meeple.api.admin

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.GatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.notNullValue

/**
 * 어드민 모임 생성 API E2E 테스트.
 * - POST /admin/v1/gatherings: 운영 생성(user_id null)으로 저장하고 gatheringId 반환. 잘못된 입력 400.
 */
class AdminGatheringCreateE2ETest : AbstractIntegrationSupport({

	describe("POST /admin/v1/gatherings") {

		context("어드민이 유효한 모임을 생성하면") {
			it("운영 생성(user_id null)으로 저장되고 gatheringId를 반환한다 (200)") {
				val response = post("/admin/v1/gatherings") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody(
						"""
						{
							"type": "PARTY", "title": "주말 파티", "description": "함께 즐겨요",
							"imageUrl": "https://cdn.test.com/party.png", "region": "서울 강남구",
							"gatheringAt": "2999-12-31T18:00:00", "capacity": 4,
							"maleFee": 10000, "femaleFee": 8000,
							"earlyBirdMaleFee": 7000, "earlyBirdFemaleFee": 5000,
							"discountMaleFee": 9000, "discountFemaleFee": 7000
						}
						""".trimIndent(),
					)
				}
				response expect {
					status(200)
					body("success", true)
					body("data.gatheringId", notNullValue())
				}
				val gatheringId: Long = response.extract().path<Int>("data.gatheringId").toLong()

				val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
				val saved: GatheringEntity = IntegrationUtil.getQuery()
					.selectFrom(gathering)
					.where(gathering.id.eq(gatheringId))
					.fetchOne()!!
				saved.userId shouldBe null
				saved.status shouldBe GatheringStatus.RECRUITING
				saved.type shouldBe GatheringType.PARTY
				saved.title shouldBe "주말 파티"
				saved.imageUrl shouldBe "https://cdn.test.com/party.png"
				saved.region shouldBe "서울 강남구"
				saved.capacity shouldBe 4
				saved.maleFee shouldBe 10000
				saved.femaleFee shouldBe 8000
				saved.earlyBirdMaleFee shouldBe 7000
				saved.discountFemaleFee shouldBe 7000
			}
		}

		context("정원이 2 미만이면") {
			it("400을 반환한다") {
				post("/admin/v1/gatherings") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody(
						"""{"type": "COOKING", "title": "쿠킹", "region": "서울", "gatheringAt": "2999-12-31T18:00:00", "capacity": 1, "maleFee": 0, "femaleFee": 0}""",
					)
				} expect {
					status(400)
					body("success", false)
				}
			}
		}

		context("얼리버드 특가를 남/녀 한쪽만 입력하면") {
			it("400을 반환한다 (GATHER-006)") {
				post("/admin/v1/gatherings") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody(
						"""{"type": "PARTY", "title": "파티", "region": "서울", "gatheringAt": "2999-12-31T18:00:00", "capacity": 4, "maleFee": 10000, "femaleFee": 8000, "earlyBirdMaleFee": 7000}""",
					)
				} expect {
					status(400)
					body("success", false)
					body("error.code", "GATHER-006")
				}
			}
		}

		context("ADMIN 권한이 없으면") {
			it("일반 사용자 토큰은 403을 반환한다") {
				post("/admin/v1/gatherings") {
					bearer(accessTokenFor(3001L))
					jsonBody(
						"""{"type": "PARTY", "title": "파티", "region": "서울", "gatheringAt": "2999-12-31T18:00:00", "capacity": 4, "maleFee": 0, "femaleFee": 0}""",
					)
				} expect {
					status(403)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
