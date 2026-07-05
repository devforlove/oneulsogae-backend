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
 * 어드민 모임 상태 변경 API E2E 테스트. (POST /admin/v1/gatherings/{id}/status)
 * 활성화(RECRUITING)·취소(CANCELED)를 하나의 엔드포인트로 처리한다. 전이 불가 409, 없으면 404.
 */
class AdminGatheringStatusE2ETest : AbstractIntegrationSupport({

	fun savedById(id: Long): GatheringEntity {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		return IntegrationUtil.getQuery().selectFrom(gathering).where(gathering.id.eq(id)).fetchOne()!!
	}

	fun persist(status: GatheringStatus): Long =
		IntegrationUtil.persist(GatheringEntityFixture.create(status = status)).id!!

	describe("POST /admin/v1/gatherings/{id}/status") {

		context("준비중(DRAFT) 모임에 status=RECRUITING이면") {
			it("활성화되어 모집중으로 전이한다 (200)") {
				val id: Long = persist(GatheringStatus.DRAFT)

				post("/admin/v1/gatherings/$id/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "RECRUITING"}""")
				} expect {
					status(200)
					body("success", true)
				}

				savedById(id).status shouldBe GatheringStatus.RECRUITING
			}
		}

		context("모집중 모임에 status=CANCELED이면") {
			it("취소되어 CANCELED로 전이한다 (200)") {
				val id: Long = persist(GatheringStatus.RECRUITING)

				post("/admin/v1/gatherings/$id/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "CANCELED"}""")
				} expect {
					status(200)
				}

				savedById(id).status shouldBe GatheringStatus.CANCELED
			}
		}

		context("준비중 모임에 status=CANCELED이면") {
			it("취소된다 (200)") {
				val id: Long = persist(GatheringStatus.DRAFT)

				post("/admin/v1/gatherings/$id/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "CANCELED"}""")
				} expect {
					status(200)
				}

				savedById(id).status shouldBe GatheringStatus.CANCELED
			}
		}

		context("이미 모집중인 모임에 status=RECRUITING이면") {
			it("409(GATHER-013)를 반환하고 상태를 바꾸지 않는다") {
				val id: Long = persist(GatheringStatus.RECRUITING)

				post("/admin/v1/gatherings/$id/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "RECRUITING"}""")
				} expect {
					status(409)
					body("success", false)
					body("error.code", "GATHER-013")
				}

				savedById(id).status shouldBe GatheringStatus.RECRUITING
			}
		}

		context("종료된 모임에 status=CANCELED이면") {
			it("409(GATHER-013)를 반환한다") {
				val id: Long = persist(GatheringStatus.FINISHED)

				post("/admin/v1/gatherings/$id/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "CANCELED"}""")
				} expect {
					status(409)
					body("error.code", "GATHER-013")
				}
			}
		}

		context("지원하지 않는 목표 상태(FINISHED)이면") {
			it("409(GATHER-013)를 반환한다") {
				val id: Long = persist(GatheringStatus.RECRUITING)

				post("/admin/v1/gatherings/$id/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "FINISHED"}""")
				} expect {
					status(409)
					body("error.code", "GATHER-013")
				}
			}
		}

		context("status가 없으면") {
			it("400을 반환한다") {
				val id: Long = persist(GatheringStatus.DRAFT)

				post("/admin/v1/gatherings/$id/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{}""")
				} expect {
					status(400)
				}
			}
		}

		context("없는 모임이면") {
			it("404(GATHER-008)를 반환한다") {
				post("/admin/v1/gatherings/999999/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "RECRUITING"}""")
				} expect {
					status(404)
					body("error.code", "GATHER-008")
				}
			}
		}

		context("ADMIN 권한이 없으면") {
			it("일반 사용자 토큰은 403을 반환한다") {
				val id: Long = persist(GatheringStatus.DRAFT)

				post("/admin/v1/gatherings/$id/status") {
					bearer(accessTokenFor(3001L))
					jsonBody("""{"status": "RECRUITING"}""")
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
