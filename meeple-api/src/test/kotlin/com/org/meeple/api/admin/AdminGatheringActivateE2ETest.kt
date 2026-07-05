package com.org.meeple.api.admin

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.GatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import io.kotest.matchers.shouldBe

/**
 * 어드민 모임 활성화 API E2E 테스트. (POST /admin/v1/gatherings/{id}/activate)
 * 준비중(DRAFT) 모임을 모집중(RECRUITING)으로 전이한다. 준비중이 아니면 409, 없으면 404.
 */
class AdminGatheringActivateE2ETest : AbstractIntegrationSupport({

	fun savedById(id: Long): GatheringEntity {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		return IntegrationUtil.getQuery().selectFrom(gathering).where(gathering.id.eq(id)).fetchOne()!!
	}

	describe("POST /admin/v1/gatherings/{id}/activate") {

		context("준비중(DRAFT) 모임을 활성화하면") {
			it("모집중(RECRUITING)으로 전이한다 (200)") {
				val id: Long = IntegrationUtil.persist(
					GatheringEntityFixture.create(title = "준비중 모임", status = GatheringStatus.DRAFT),
				).id!!

				post("/admin/v1/gatherings/$id/activate") {
					bearer(adminAccessTokenFor(9901L))
				} expect {
					status(200)
					body("success", true)
				}

				savedById(id).status shouldBe GatheringStatus.RECRUITING
			}
		}

		context("이미 모집중인 모임을 활성화하면") {
			it("409(GATHER-013)를 반환하고 상태를 바꾸지 않는다") {
				val id: Long = IntegrationUtil.persist(
					GatheringEntityFixture.create(title = "모집중 모임", status = GatheringStatus.RECRUITING),
				).id!!

				post("/admin/v1/gatherings/$id/activate") {
					bearer(adminAccessTokenFor(9901L))
				} expect {
					status(409)
					body("success", false)
					body("error.code", "GATHER-013")
				}

				savedById(id).status shouldBe GatheringStatus.RECRUITING
			}
		}

		context("없는 모임을 활성화하면") {
			it("404(GATHER-008)를 반환한다") {
				post("/admin/v1/gatherings/999999/activate") {
					bearer(adminAccessTokenFor(9901L))
				} expect {
					status(404)
					body("error.code", "GATHER-008")
				}
			}
		}

		context("ADMIN 권한이 없으면") {
			it("일반 사용자 토큰은 403을 반환한다") {
				val id: Long = IntegrationUtil.persist(
					GatheringEntityFixture.create(status = GatheringStatus.DRAFT),
				).id!!

				post("/admin/v1/gatherings/$id/activate") {
					bearer(accessTokenFor(3001L))
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
