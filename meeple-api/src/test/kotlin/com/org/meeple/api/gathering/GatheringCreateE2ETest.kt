package com.org.meeple.api.gathering

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

class GatheringCreateE2ETest : AbstractIntegrationSupport({

	describe("POST /gatherings/v1") {

		context("로그인 사용자가 유효한 모임을 생성하면") {
			it("RECRUITING 상태로 저장되고 gatheringId를 반환한다 (200)") {
				val userId = 2001L

				val response = post("/gatherings/v1") {
					bearer(accessTokenFor(userId))
					jsonBody(
						"""{"type": "PARTY", "title": "주말 파티", "description": "함께 즐겨요", "regionId": 10, "gatheringAt": "2999-12-31T18:00:00", "capacity": 4, "fee": 10000}""",
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
				saved.userId shouldBe userId
				saved.status shouldBe GatheringStatus.RECRUITING
				saved.type shouldBe GatheringType.PARTY
				saved.title shouldBe "주말 파티"
				saved.regionId shouldBe 10L
				saved.capacity shouldBe 4
				saved.fee shouldBe 10000
			}
		}

		context("정원이 2 미만이면") {
			it("400을 반환한다") {
				post("/gatherings/v1") {
					bearer(accessTokenFor(2002L))
					jsonBody(
						"""{"type": "COOKING", "title": "쿠킹", "regionId": 10, "gatheringAt": "2999-12-31T18:00:00", "capacity": 1, "fee": 0}""",
					)
				} expect {
					status(400)
					body("success", false)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/gatherings/v1") {
					jsonBody(
						"""{"type": "PARTY", "title": "파티", "regionId": 10, "gatheringAt": "2999-12-31T18:00:00", "capacity": 4, "fee": 0}""",
					)
				} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
