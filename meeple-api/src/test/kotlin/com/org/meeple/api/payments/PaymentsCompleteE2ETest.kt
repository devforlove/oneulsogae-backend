package com.org.meeple.api.payments
import io.kotest.core.annotation.Ignored

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.config.FakePaymentGateway
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringMemberEntityFixture
import com.org.meeple.infra.fixture.GatheringProductEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.GatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.infra.payments.command.entity.PaymentEntity
import com.org.meeple.infra.payments.command.entity.QPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /payments/v1/complete` E2E 테스트.
 *
 * 좌석 확보 후 PG 승인을 거쳐 결제완료를 접수한다: 본인 프로필 성별을 강제해 참가를 승인대기(PENDING)로 등록하고 결제 기록을 남긴다.
 * 상품은 productId로 지정한다(모임 상세 응답의 schedules[].productId).
 * - 결제액은 요청한 상품(productId)의 티어 저장가로 확정한다(체크아웃에서 본 금액과 일치).
 * - 성별 여분·얼리버드 여분을 접수 시점에 차감한다(PENDING도 정원 포함).
 * - 매진 409(GATHERING-004), 얼리버드 소진 409(GATHERING-007), 예정 아닌 일정 409(GATHERING-003),
 *   중복 접수 409(GATHERING-005), 상품 없음 404(GATHERING-006), 타성별 상품 400(PAYMENTS-003), 성별 미확정 400(PAYMENTS-002).
 * - 거절/취소 행은 재접수 시 PENDING으로 되살린다.
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
class PaymentsCompleteE2ETest : AbstractIntegrationSupport({

	// 성별이 확정된 유저를 저장하고 userId를 돌려준다.
	fun persistUserWithGender(providerId: String, gender: Gender = Gender.MALE): Long {
		val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = gender))
		return userId
	}

	// 모집중 모임 + 일정 1건을 저장하고 (gatheringId, scheduleId, 남성 NORMAL productId)를 돌려준다.
	fun persistGatheringWithSchedule(
		maleRemaining: Int = 4,
		earlyBirdDiscountRate: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
	): Triple<Long, Long, Long> {
		val gatheringId: Long = IntegrationUtil.persist(
			GatheringEntityFixture.create(status = GatheringStatus.RECRUITING),
		).id!!
		val scheduleId: Long = IntegrationUtil.persist(
			GatheringScheduleEntityFixture.create(
				gatheringId = gatheringId,
				maleRemaining = maleRemaining,
				earlyBirdCapacity = earlyBirdCapacity,
				earlyBirdRemaining = earlyBirdRemaining,
				status = status,
			),
		).id!!
		val products: List<GatheringProductEntity> = GatheringProductEntityFixture.tierSet(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			maleFee = 10000,
			femaleFee = 8000,
			earlyBirdDiscountRate = earlyBirdDiscountRate,
			discountMaleFee = null,
			discountFemaleFee = null,
		).map { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
		val productId: Long = products.first { product: GatheringProductEntity ->
			product.gender == Gender.MALE && product.type == GatheringProductType.NORMAL
		}.id!!
		return Triple(gatheringId, scheduleId, productId)
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

	// 일정의 특정 성별·티어 상품 id를 조회한다.
	fun productIdOf(scheduleId: Long, gender: Gender, type: GatheringProductType): Long {
		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		return IntegrationUtil.getQuery().selectFrom(product)
			.where(product.scheduleId.eq(scheduleId), product.gender.eq(gender), product.type.eq(type))
			.fetchOne()!!.id!!
	}

	describe("POST /payments/v1/complete") {

		context("성별이 확정된 유저가 얼리버드 상품(EARLY_BIRD)으로 결제완료하면") {
			it("얼리버드가로 PENDING 참가·결제 기록을 남기고 성별·얼리버드 여분을 차감한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-1", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long, _: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 2,
				)
				val earlyBirdProductId: Long = productIdOf(scheduleId, Gender.MALE, GatheringProductType.EARLY_BIRD)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $earlyBirdProductId, "paymentKey": "pay_key_1", "orderId": "ord_pay_key_1"}""")
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
				// 가격 근거: 요청에 쓴 상품 id가 결제 기록에 남는다.
				saved?.productId shouldBe earlyBirdProductId
				saved?.paymentKey shouldBe "pay_key_1"
				saved?.orderId shouldBe "ord_pay_key_1"
				saved?.status shouldBe PaymentStatus.APPROVED
			}
		}

		context("정가 상품(NORMAL)으로 결제완료하면") {
			it("얼리버드가 유효해도 요청한 정가로 접수하고 얼리버드 여분을 차감하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-normal", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long, normalProductId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 2,
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $normalProductId, "paymentKey": "pay_key_normal", "orderId": "ord_pay_key_normal"}""")
				} expect {
					status(200)
					body("data.amount", 10000)
				}

				findMember(scheduleId, userId)?.earlyBirdApplied shouldBe false
				findSchedule(scheduleId)?.earlyBirdRemaining shouldBe 2
			}
		}

		context("얼리버드 상품(EARLY_BIRD)으로 결제완료하지만 얼리버드가 이미 소진되었으면") {
			it("409 GATHERING-007을 반환하고 아무것도 저장하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-eb-soldout", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long, _: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 2,
					earlyBirdRemaining = 0,
				)
				val earlyBirdProductId: Long = productIdOf(scheduleId, Gender.MALE, GatheringProductType.EARLY_BIRD)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $earlyBirdProductId, "paymentKey": "pay_key_soldout", "orderId": "ord_pay_key_soldout"}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-007")
				}

				findMember(scheduleId, userId) shouldBe null
				findSchedule(scheduleId)?.maleRemaining shouldBe 4
				findSchedule(scheduleId)?.earlyBirdRemaining shouldBe 0
			}
		}

		context("해당 성별 여분이 없는 일정에 결제완료하면") {
			it("409 GATHERING-004를 반환하고 아무것도 저장하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-2", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule(maleRemaining = 0)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $productId, "paymentKey": "pay_key_no_gender_remaining", "orderId": "ord_pay_key_no_gender_remaining"}""")
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
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule(
					status = GatheringScheduleStatus.COMPLETED,
				)

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $productId, "paymentKey": "pay_key_not_scheduled", "orderId": "ord_pay_key_not_scheduled"}""")
				} expect {
					status(409)
					body("error.code", "GATHERING-003")
				}
			}
		}

		context("이미 승인대기 접수가 있는 일정에 다시 결제완료하면") {
			it("409 GATHERING-005를 반환하고 여분을 추가 차감하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-4", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $productId, "paymentKey": "pay_key_dup_1", "orderId": "ord_pay_key_dup_1"}""")
				} expect { status(200) }

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $productId, "paymentKey": "pay_key_dup_2", "orderId": "ord_pay_key_dup_2"}""")
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
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule()
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
					jsonBody("""{"productId": $productId, "paymentKey": "pay_key_rejected_revive", "orderId": "ord_pay_key_rejected_revive"}""")
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
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $productId, "paymentKey": "pay_key_no_gender", "orderId": "ord_pay_key_no_gender"}""")
				} expect {
					status(400)
					body("error.code", "PAYMENTS-002")
				}
			}
		}

		context("없는 productId로 결제완료하면") {
			it("404 GATHERING-006을 반환한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-nf")

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": 999999, "paymentKey": "pay_key_not_found", "orderId": "ord_pay_key_not_found"}""")
				} expect {
					status(404)
					body("error.code", "GATHERING-006")
				}
			}
		}

		context("PG 승인(confirm)이 실패하면") {
			it("402 PAYMENTS-004를 반환하고 좌석·여분을 복원하며 결제 기록을 FAILED로 남긴다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-confirm-fail", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long, normalProductId: Long) = persistGatheringWithSchedule()
				FakePaymentGateway.result = FakePaymentGateway.REJECTED

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $normalProductId, "paymentKey": "pay_key_fail", "orderId": "ord_pay_key_fail"}""")
				} expect {
					status(402)
					body("error.code", "PAYMENTS-004")
				}

				// 좌석 복원: 여분 원복, 참가는 CANCELED. 결제 기록은 FAILED로 보존(이력 추적).
				findSchedule(scheduleId)?.maleRemaining shouldBe 4
				findMember(scheduleId, userId)?.status shouldBe GatheringMemberStatus.CANCELED

				val payment: QPaymentEntity = QPaymentEntity.paymentEntity
				val failed: PaymentEntity? = IntegrationUtil.getQuery().selectFrom(payment)
					.where(payment.scheduleId.eq(scheduleId), payment.userId.eq(userId))
					.fetchOne()
				failed?.status shouldBe PaymentStatus.FAILED
				failed?.paymentKey shouldBe "pay_key_fail"
				failed?.failReason shouldBe FakePaymentGateway.REJECTED.failReason
			}
		}

		context("타성별 상품의 productId로 결제완료하면") {
			it("400 PAYMENTS-003을 반환하고 아무것도 저장하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-gm", gender = Gender.FEMALE)
				val (gatheringId: Long, scheduleId: Long, maleProductId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $maleProductId, "paymentKey": "pay_key_gender_mismatch", "orderId": "ord_pay_key_gender_mismatch"}""")
				} expect {
					status(400)
					body("error.code", "PAYMENTS-003")
				}

				findMember(scheduleId, userId) shouldBe null
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
	}
})
