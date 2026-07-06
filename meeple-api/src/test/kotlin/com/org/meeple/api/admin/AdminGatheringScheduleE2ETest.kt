package com.org.meeple.api.admin

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue

/**
 * 어드민 모임 일정 API E2E 테스트.
 * - POST /admin/v1/gatherings/{gatheringId}/schedules : 일정 생성(예정)
 * - POST /admin/v1/gatherings/{gatheringId}/schedules/{scheduleId}/status : 상태 전이
 */
class AdminGatheringScheduleE2ETest : AbstractIntegrationSupport({

	fun persistGathering(): Long =
		IntegrationUtil.persist(GatheringEntityFixture.create()).id!!

	fun persistSchedule(gatheringId: Long, status: GatheringScheduleStatus): Long =
		IntegrationUtil.persist(GatheringScheduleEntityFixture.create(gatheringId = gatheringId, status = status)).id!!

	fun savedById(id: Long): GatheringScheduleEntity {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return IntegrationUtil.getQuery().selectFrom(schedule).where(schedule.id.eq(id)).fetchOne()!!
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
						 "earlyBirdMaleFee": 7000, "earlyBirdFemaleFee": 5000, "earlyBirdCapacity": 2,
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
				saved.maleFee shouldBe 10000
				saved.femaleFee shouldBe 8000
				// 정원은 모임 정원(fixture 기본 4)의 절반(2)으로 정해진다.
				saved.maleCapacity shouldBe 2
				saved.femaleCapacity shouldBe 2
				// 저장 시 남/녀 여분은 각 성별 정원으로 초기화된다.
				saved.maleRemaining shouldBe 2
				saved.femaleRemaining shouldBe 2
				saved.earlyBirdMaleFee shouldBe 7000
				saved.earlyBirdCapacity shouldBe 2
				// 저장 시 남은 개수는 정원(earlyBirdCapacity)으로 초기화된다.
				saved.earlyBirdRemaining shouldBe 2
				saved.discountMaleFee shouldBe 9000
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
				saved.earlyBirdMaleFee shouldBe null
				saved.earlyBirdCapacity shouldBe null
				saved.earlyBirdRemaining shouldBe null
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
						 "earlyBirdMaleFee": 7000, "earlyBirdFemaleFee": 5000, "earlyBirdCapacity": 5}""",
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
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
