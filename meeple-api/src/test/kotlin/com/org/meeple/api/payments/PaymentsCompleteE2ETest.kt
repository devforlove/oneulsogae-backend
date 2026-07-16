package com.org.meeple.api.payments

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringMemberEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.infra.payments.command.entity.PaymentEntity
import com.org.meeple.infra.payments.command.entity.QPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /payments/v1/complete` E2E 테스트.
 *
 * 무검증 결제완료 접수: 본인 프로필 성별을 강제해 참가를 승인대기(PENDING)로 등록하고 결제 기록을 남긴다.
 * - 성별 여분·얼리버드 여분을 접수 시점에 차감한다(PENDING도 정원 포함).
 * - 매진 409(GATHERING-004), 예정 아닌 일정 409(GATHERING-003), 중복 접수 409(GATHERING-005),
 *   일정 없음 404(GATHERING-002), 성별 미확정 400(PAYMENTS-002).
 * - 거절/취소 행은 재접수 시 PENDING으로 되살린다.
 */
class PaymentsCompleteE2ETest : AbstractIntegrationSupport({

	// 성별이 확정된 유저를 저장하고 userId를 돌려준다.
	fun persistUserWithGender(providerId: String, gender: Gender = Gender.MALE): Long {
		val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = gender))
		return userId
	}

	// 모집중 모임 + 일정 1건을 저장하고 (gatheringId, scheduleId)를 돌려준다.
	fun persistGatheringWithSchedule(
		maleRemaining: Int = 4,
		earlyBirdDiscountRate: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
	): Pair<Long, Long> {
		val gatheringId: Long = IntegrationUtil.persist(
			GatheringEntityFixture.create(status = GatheringStatus.RECRUITING),
		).id!!
		val scheduleId: Long = IntegrationUtil.persist(
			GatheringScheduleEntityFixture.create(
				gatheringId = gatheringId,
				maleFee = 10000,
				femaleFee = 8000,
				maleRemaining = maleRemaining,
				earlyBirdDiscountRate = earlyBirdDiscountRate,
				earlyBirdCapacity = earlyBirdCapacity,
				earlyBirdRemaining = earlyBirdRemaining,
				status = status,
			),
		).id!!
		return gatheringId to scheduleId
	}

	fun findMember(scheduleId: Long, userId: Long): GatheringMemberEntity? {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		return IntegrationUtil.getQuery().selectFrom(member)
			.where(member.scheduleId.eq(scheduleId), member.userId.eq(userId))
			.fetchOne()
	}

	fun findSchedule(scheduleId: Long): GatheringScheduleEntity? {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return IntegrationUtil.getQuery().selectFrom(schedule).where(schedule.id.eq(scheduleId)).fetchOne()
	}

	describe("POST /payments/v1/complete") {

		context("성별이 확정된 유저가 얼리버드 유효 일정에 결제완료하면") {
			it("PENDING 참가·결제 기록을 남기고 성별·얼리버드 여분을 차감한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-1", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 2,
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(200)
					body("success", true)
					body("data.amount", 7000)
				}

				val member: GatheringMemberEntity? = findMember(scheduleId, userId)
				member?.status shouldBe GatheringMemberStatus.PENDING
				member?.gender shouldBe Gender.MALE
				member?.earlyBirdApplied shouldBe true

				val schedule: GatheringScheduleEntity? = findSchedule(scheduleId)
				schedule?.maleRemaining shouldBe 3
				schedule?.earlyBirdRemaining shouldBe 1

				val payment: QPaymentEntity = QPaymentEntity.paymentEntity
				val saved: PaymentEntity? = IntegrationUtil.getQuery().selectFrom(payment)
					.where(payment.scheduleId.eq(scheduleId), payment.userId.eq(userId))
					.fetchOne()
				saved?.amount shouldBe 7000
				saved?.gender shouldBe Gender.MALE
			}
		}

		context("해당 성별 여분이 없는 일정에 결제완료하면") {
			it("409 GATHERING-004를 반환하고 아무것도 저장하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-2", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(maleRemaining = 0)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-004")
				}

				findMember(scheduleId, userId) shouldBe null
			}
		}

		context("예정 상태가 아닌 일정에 결제완료하면") {
			it("409 GATHERING-003을 반환한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-3")
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					status = GatheringScheduleStatus.COMPLETED,
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-003")
				}
			}
		}

		context("이미 승인대기 접수가 있는 일정에 다시 결제완료하면") {
			it("409 GATHERING-005를 반환하고 여분을 추가 차감하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-4", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect { status(200) }

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-005")
				}

				findSchedule(scheduleId)?.maleRemaining shouldBe 3
			}
		}

		context("거절된 접수가 있는 유저가 다시 결제완료하면") {
			it("기존 행을 PENDING으로 되살리고 여분을 다시 차감한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-5", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = gatheringId,
						scheduleId = scheduleId,
						userId = userId,
						gender = Gender.MALE,
						status = GatheringMemberStatus.REJECTED,
					),
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(200)
					body("data.amount", 10000)
				}

				findMember(scheduleId, userId)?.status shouldBe GatheringMemberStatus.PENDING
				findSchedule(scheduleId)?.maleRemaining shouldBe 3
			}
		}

		context("성별이 없는 유저가 결제완료하면") {
			it("400 PAYMENTS-002를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "pay-complete-6")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = null))
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": $scheduleId}""")
				} expect {
					status(400)
					body("error.code", "PAYMENTS-002")
				}
			}
		}

		context("없는 일정으로 결제완료하면") {
			it("404 GATHERING-002를 반환한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-7")
				val (gatheringId: Long, _) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"gatheringId": $gatheringId, "scheduleId": 999999}""")
				} expect {
					status(404)
					body("error.code", "GATHERING-002")
				}
			}
		}
	}
})
