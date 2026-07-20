package com.org.oneulsogae.api.admin
import io.kotest.core.annotation.Ignored

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringScheduleEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue

/**
 * 어드민 모임 일정 API E2E 테스트.
 * - POST /admin/v1/gatherings/{gatheringId}/schedules : 일정 생성(예정)
 * - POST /admin/v1/gatherings/{gatheringId}/schedules/{scheduleId}/status : 상태 전이
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
class AdminGatheringScheduleE2ETest : AbstractIntegrationSupport({

	fun persistGathering(): Long =
		IntegrationUtil.persist(GatheringEntityFixture.create()).id!!

	fun persistSchedule(gatheringId: Long, status: GatheringScheduleStatus): Long =
		IntegrationUtil.persist(GatheringScheduleEntityFixture.create(gatheringId = gatheringId, status = status)).id!!

	fun savedById(id: Long): GatheringScheduleEntity {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return IntegrationUtil.getQuery().selectFrom(schedule).where(schedule.id.eq(id)).fetchOne()!!
	}

	fun productsByScheduleId(scheduleId: Long): List<GatheringProductEntity> {
		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		return IntegrationUtil.getQuery().selectFrom(product).where(product.scheduleId.eq(scheduleId)).fetch()
	}

	describe("POST /admin/v1/gatherings/{gatheringId}/schedules") {

		context("어드민이 유효한 일정을 생성하면") {
			it("예정(SCHEDULED) 상태로 저장하고 scheduleId를 반환한다 (200)") {
				val gatheringId: Long = persistGathering()

				val scheduleId: Long = RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.contentType("application/json")
					.body(
						"""
						{"startAt": "2999-12-31T18:00:00", "endAt": "2999-12-31T20:00:00",
						 "maleFee": 10000, "femaleFee": 8000,
						 "earlyBirdDiscountRate": 30, "earlyBirdCapacity": 2,
						 "discountMaleFee": 9000, "discountFemaleFee": 7000}
						""".trimIndent(),
					)
					.post("/admin/v1/gatherings/$gatheringId/schedules")
					.then()
					.statusCode(200)
					.body("success", equalTo(true))
					.body("data.scheduleId", notNullValue())
					.extract().path<Int>("data.scheduleId").toLong()

				val saved: GatheringScheduleEntity = savedById(scheduleId)
				saved.gatheringId shouldBe gatheringId
				saved.status shouldBe GatheringScheduleStatus.SCHEDULED
				// 정원은 모임 정원(fixture 기본 4)의 절반(2)으로 정해진다.
				saved.maleCapacity shouldBe 2
				saved.femaleCapacity shouldBe 2
				// 저장 시 남/녀 여분은 각 성별 정원으로 초기화된다.
				saved.maleRemaining shouldBe 2
				saved.femaleRemaining shouldBe 2
				saved.earlyBirdCapacity shouldBe 2
				// 저장 시 남은 개수는 정원(earlyBirdCapacity)으로 초기화된다.
				saved.earlyBirdRemaining shouldBe 2
				// 성별·티어별 상품이 함께 생성된다: 남/녀 × (NORMAL, EARLY_BIRD, DISCOUNT) = 6행.
				val products: List<GatheringProductEntity> = productsByScheduleId(scheduleId)
				products.size shouldBe 6
				products.first { it.gender == Gender.MALE && it.type == GatheringProductType.NORMAL }.price shouldBe 10000
				products.first { it.gender == Gender.FEMALE && it.type == GatheringProductType.NORMAL }.price shouldBe 8000
				// 얼리버드가 = 정가 × 70% (할인율 30).
				products.first { it.gender == Gender.MALE && it.type == GatheringProductType.EARLY_BIRD }.price shouldBe 7000
				products.first { it.gender == Gender.FEMALE && it.type == GatheringProductType.EARLY_BIRD }.price shouldBe 5600
				products.first { it.gender == Gender.MALE && it.type == GatheringProductType.DISCOUNT }.price shouldBe 9000
				products.first { it.gender == Gender.FEMALE && it.type == GatheringProductType.DISCOUNT }.price shouldBe 7000
				products.all { it.gatheringId == gatheringId } shouldBe true
			}
		}

		context("종료 시각·특가 없이 정상가만 생성하면") {
			it("end_at·특가가 null로 저장된다 (200)") {
				val gatheringId: Long = persistGathering()

				val scheduleId: Long = RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.contentType("application/json")
					.body("""{"startAt": "2999-12-31T18:00:00", "maleFee": 10000, "femaleFee": 8000}""")
					.post("/admin/v1/gatherings/$gatheringId/schedules")
					.then()
					.statusCode(200)
					.extract().path<Int>("data.scheduleId").toLong()

				val saved: GatheringScheduleEntity = savedById(scheduleId)
				saved.endAt shouldBe null
				// 남/녀 여분은 모임 정원(4)의 절반(2)으로 초기화된다.
				saved.maleRemaining shouldBe 2
				saved.femaleRemaining shouldBe 2
				saved.earlyBirdCapacity shouldBe null
				saved.earlyBirdRemaining shouldBe null
				// 정가만 있으면 상품은 남/녀 NORMAL 2행이다.
				val products: List<GatheringProductEntity> = productsByScheduleId(scheduleId)
				products.size shouldBe 2
				products.all { it.type == GatheringProductType.NORMAL } shouldBe true
			}
		}

		context("정상가(남/녀)가 없으면") {
			it("400을 반환한다") {
				val gatheringId: Long = persistGathering()

				post("/admin/v1/gatherings/$gatheringId/schedules") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"startAt": "2999-12-31T18:00:00"}""")
				} expect {
					status(400)
				}
			}
		}

		context("얼리버드 적용 인원이 모임 정원을 초과하면") {
			it("400(GATHER-012)를 반환한다") {
				// 모임 정원(fixture 기본) = 4. earlyBirdCapacity=5는 정원 초과.
				val gatheringId: Long = persistGathering()

				post("/admin/v1/gatherings/$gatheringId/schedules") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody(
						"""{"startAt": "2999-12-31T18:00:00", "maleFee": 10000, "femaleFee": 8000,
						 "earlyBirdDiscountRate": 30, "earlyBirdCapacity": 5}""",
					)
				} expect {
					status(400)
					body("error.code", "GATHER-012")
				}
			}
		}

		context("없는 모임에 생성하면") {
			it("404(GATHER-008)를 반환한다") {
				post("/admin/v1/gatherings/999999/schedules") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"startAt": "2999-12-31T18:00:00", "maleFee": 10000, "femaleFee": 8000}""")
				} expect {
					status(404)
					body("error.code", "GATHER-008")
				}
			}
		}

		context("시작 시각이 과거이면") {
			it("400(GATHER-015)를 반환한다") {
				val gatheringId: Long = persistGathering()

				post("/admin/v1/gatherings/$gatheringId/schedules") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"startAt": "2000-01-01T18:00:00", "maleFee": 10000, "femaleFee": 8000}""")
				} expect {
					status(400)
					body("error.code", "GATHER-015")
				}
			}
		}

		context("종료 시각이 시작 시각 이전이면") {
			it("400(GATHER-016)를 반환한다") {
				val gatheringId: Long = persistGathering()

				post("/admin/v1/gatherings/$gatheringId/schedules") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody(
						"""{"startAt": "2999-12-31T18:00:00", "endAt": "2999-12-31T17:00:00", "maleFee": 10000, "femaleFee": 8000}""",
					)
				} expect {
					status(400)
					body("error.code", "GATHER-016")
				}
			}
		}

		context("시작 시각이 없으면") {
			it("400을 반환한다") {
				val gatheringId: Long = persistGathering()

				post("/admin/v1/gatherings/$gatheringId/schedules") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{}""")
				} expect {
					status(400)
				}
			}
		}

		context("ADMIN 권한이 없으면") {
			it("일반 사용자 토큰은 403을 반환한다") {
				val gatheringId: Long = persistGathering()

				post("/admin/v1/gatherings/$gatheringId/schedules") {
					bearer(accessTokenFor(3001L))
					jsonBody("""{"startAt": "2999-12-31T18:00:00"}""")
				} expect {
					status(403)
				}
			}
		}
	}

	describe("POST /admin/v1/gatherings/{gatheringId}/schedules/{scheduleId}/status") {

		context("예정(SCHEDULED) 일정에 status=COMPLETED이면") {
			it("종료로 전이한다 (200)") {
				val gatheringId: Long = persistGathering()
				val scheduleId: Long = persistSchedule(gatheringId, GatheringScheduleStatus.SCHEDULED)

				post("/admin/v1/gatherings/$gatheringId/schedules/$scheduleId/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "COMPLETED"}""")
				} expect {
					status(200)
					body("success", true)
				}

				savedById(scheduleId).status shouldBe GatheringScheduleStatus.COMPLETED
			}
		}

		context("예정 일정에 status=CANCELED이면") {
			it("취소된다 (200)") {
				val gatheringId: Long = persistGathering()
				val scheduleId: Long = persistSchedule(gatheringId, GatheringScheduleStatus.SCHEDULED)

				post("/admin/v1/gatherings/$gatheringId/schedules/$scheduleId/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "CANCELED"}""")
				} expect {
					status(200)
				}

				savedById(scheduleId).status shouldBe GatheringScheduleStatus.CANCELED
			}
		}

		context("이미 종료된 일정을 전이하려 하면") {
			it("409(GATHER-017)를 반환하고 상태를 바꾸지 않는다") {
				val gatheringId: Long = persistGathering()
				val scheduleId: Long = persistSchedule(gatheringId, GatheringScheduleStatus.COMPLETED)

				post("/admin/v1/gatherings/$gatheringId/schedules/$scheduleId/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "CANCELED"}""")
				} expect {
					status(409)
					body("error.code", "GATHER-017")
				}

				savedById(scheduleId).status shouldBe GatheringScheduleStatus.COMPLETED
			}
		}

		context("이미 취소된 일정을 전이하려 하면") {
			it("409(GATHER-017)를 반환한다") {
				val gatheringId: Long = persistGathering()
				val scheduleId: Long = persistSchedule(gatheringId, GatheringScheduleStatus.CANCELED)

				post("/admin/v1/gatherings/$gatheringId/schedules/$scheduleId/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "COMPLETED"}""")
				} expect {
					status(409)
					body("error.code", "GATHER-017")
				}
			}
		}

		context("일정이 요청 경로의 모임 소속이 아니면") {
			it("404(GATHER-014)를 반환한다") {
				val gatheringId: Long = persistGathering()
				val otherGatheringId: Long = persistGathering()
				val scheduleId: Long = persistSchedule(otherGatheringId, GatheringScheduleStatus.SCHEDULED)

				post("/admin/v1/gatherings/$gatheringId/schedules/$scheduleId/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "COMPLETED"}""")
				} expect {
					status(404)
					body("error.code", "GATHER-014")
				}
			}
		}

		context("없는 일정이면") {
			it("404(GATHER-014)를 반환한다") {
				val gatheringId: Long = persistGathering()

				post("/admin/v1/gatherings/$gatheringId/schedules/999999/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{"status": "COMPLETED"}""")
				} expect {
					status(404)
					body("error.code", "GATHER-014")
				}
			}
		}

		context("status가 없으면") {
			it("400을 반환한다") {
				val gatheringId: Long = persistGathering()
				val scheduleId: Long = persistSchedule(gatheringId, GatheringScheduleStatus.SCHEDULED)

				post("/admin/v1/gatherings/$gatheringId/schedules/$scheduleId/status") {
					bearer(adminAccessTokenFor(9901L))
					jsonBody("""{}""")
				} expect {
					status(400)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
