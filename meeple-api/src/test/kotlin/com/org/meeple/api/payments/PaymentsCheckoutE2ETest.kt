package com.org.meeple.api.payments

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.core.user.command.domain.IdentityVerificationStatus
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringProductEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IdentityVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.PaymentMethodEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.gathering.command.entity.GatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.infra.payments.command.entity.QPaymentMethodEntity
import com.org.meeple.infra.user.command.entity.QIdentityVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDateTime

/**
 * `GET /payments/v1/checkout?gatheringId=&scheduleId=&gender=` E2E 테스트.
 *
 * 결제(체크아웃) 화면 진입 시 주문자 정보 + 상품(모임 일정) 정보 + 활성 결제수단 목록을 반환한다.
 * - 주문자: 실명(최신 VERIFIED 본인인증)·이메일(users)·휴대폰(user_details). 미비는 null 필드(에러 아님).
 * - 상품: 정가(price)와 서버 확정 실결제가(salePrice — 얼리버드 유효 시 얼리버드가, 소진 시 할인가, 그 외 정가), 매진은 soldOut 플래그(200).
 * - 결제수단: active만 displayOrder 순.
 * - 없는 일정은 404(PAYMENTS-001), 모임 없음/모집중 아님은 404(GATHERING-001).
 * (presigned URL은 TestFileStorageConfig의 페이크 — https://presigned.test/<imageKey>)
 */
class PaymentsCheckoutE2ETest : AbstractIntegrationSupport({

	// 모집중 모임 + 일정 1건을 저장하고 (gatheringId, scheduleId)를 돌려준다.
	fun persistGatheringWithSchedule(
		earlyBirdDiscountRate: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		discountMaleFee: Int? = null,
		maleRemaining: Int = 4,
	): Pair<Long, Long> {
		val gatheringId: Long = IntegrationUtil.persist(
			GatheringEntityFixture.create(
				title = "체크아웃 모임",
				imageKey = "gatherings/checkout.png",
				region = "서울 강남구",
				status = GatheringStatus.RECRUITING,
			),
		).id!!
		val scheduleId: Long = IntegrationUtil.persist(
			GatheringScheduleEntityFixture.create(
				gatheringId = gatheringId,
				startAt = LocalDateTime.of(2999, 1, 1, 19, 0, 0),
				maleFee = 10000,
				femaleFee = 8000,
				maleRemaining = maleRemaining,
				earlyBirdDiscountRate = earlyBirdDiscountRate,
				earlyBirdCapacity = earlyBirdCapacity,
				earlyBirdRemaining = earlyBirdRemaining,
				discountMaleFee = discountMaleFee,
			),
		).id!!
		GatheringProductEntityFixture.tierSet(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			maleFee = 10000,
			femaleFee = 8000,
			earlyBirdDiscountRate = earlyBirdDiscountRate,
			discountMaleFee = discountMaleFee,
		).forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
		return gatheringId to scheduleId
	}

	describe("GET /payments/v1/checkout") {

		context("본인인증 유저가 얼리버드가 유효한 일정을 조회하면") {
			it("주문자·상품(얼리버드 실결제가)·활성 결제수단만 순서대로 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-1", email = "orderer@test.com"),
				)
				val userId: Long = user.id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, phoneNumber = "01011112222"))
				// 재인증 이력: 과거 VERIFIED → 최신 VERIFIED → 진행 중(REQUESTED). 최신 VERIFIED("김미플")가 선택되어야 한다.
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김과거"))
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김미플"))
				IntegrationUtil.persist(
					IdentityVerificationEntityFixture.create(
						userId = userId,
						status = IdentityVerificationStatus.REQUESTED,
						realName = null,
						verifiedAt = null,
					),
				)
				// 얼리버드 30% 유효(remaining 5) → 남성 salePrice = 10000×0.7 = 7000.
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
				)
				// 활성 2건(순서 역순 저장으로 정렬 검증) + 비활성 1건(제외 검증).
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "KAKAO_PAY", name = "카카오페이", displayOrder = 2))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "BANK_TRANSFER", name = "무통장입금", displayOrder = 1))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "CARD", name = "카드", displayOrder = 3, active = false))

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.orderer.name", "김미플")
					body("data.orderer.email", "orderer@test.com")
					body("data.orderer.phoneNumber", "01011112222")
					body("data.product.gatheringId", gatheringId.toInt())
					body("data.product.scheduleId", scheduleId.toInt())
					body("data.product.gender", "MALE")
					body("data.product.title", "체크아웃 모임")
					body("data.product.imageUrl", "https://presigned.test/gatherings/checkout.png")
					body("data.product.region", "서울 강남구")
					body("data.product.startAt", "2999-01-01T19:00:00")
					body("data.product.price", 10000)
					body("data.product.salePrice", 7000)
					body("data.product.soldOut", false)
					body("data.paymentMethods", hasSize<Any>(2))
					body("data.paymentMethods.code", contains("BANK_TRANSFER", "KAKAO_PAY"))
					body("data.paymentMethods[0].name", "무통장입금")
				}
			}
		}

		context("얼리버드가 소진된 일정을 조회하면") {
			it("실결제가는 할인가다") {
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
					earlyBirdRemaining = 0,
					discountMaleFee = 9000,
				)

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(9001L))
				} expect {
					status(200)
					body("data.product.price", 10000)
					body("data.product.salePrice", 9000)
				}
			}
		}

		context("얼리버드 티어가 없는 일정을 조회하면") {
			it("실결제가는 정가다") {
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(9002L))
				} expect {
					status(200)
					body("data.product.price", 10000)
					body("data.product.salePrice", 10000)
				}
			}
		}

		context("해당 성별 정원이 소진된 일정을 조회하면") {
			it("200과 함께 soldOut을 true로 반환한다") {
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(maleRemaining = 0)

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(9003L))
				} expect {
					status(200)
					body("data.product.soldOut", true)
				}
			}
		}

		context("모임에 없는 scheduleId로 조회하면") {
			it("404(PAYMENTS-001)를 반환한다") {
				val (gatheringId: Long, _) = persistGatheringWithSchedule()

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=999999&gender=MALE") {
					bearer(accessTokenFor(9004L))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "PAYMENTS-001")
				}
			}
		}

		context("모집중이 아닌 모임으로 조회하면") {
			it("404(GATHERING-001)를 반환한다") {
				val gatheringId: Long = IntegrationUtil.persist(
					GatheringEntityFixture.create(title = "취소된 모임", status = GatheringStatus.CANCELED),
				).id!!

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=1&gender=MALE") {
					bearer(accessTokenFor(9005L))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "GATHERING-001")
				}
			}
		}

		context("프로필·본인인증이 없는 사용자가 조회하면") {
			it("주문자 필드는 null이고 상품·결제수단은 정상 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-2", email = null),
				)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()
				IntegrationUtil.persist(PaymentMethodEntityFixture.create())

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(user.id!!))
				} expect {
					status(200)
					body("data.orderer.name", nullValue())
					body("data.orderer.email", nullValue())
					body("data.orderer.phoneNumber", nullValue())
					body("data.product.price", 10000)
					body("data.paymentMethods", hasSize<Any>(1))
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/payments/v1/checkout?gatheringId=1&scheduleId=1&gender=MALE") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPaymentMethodEntity.paymentMethodEntity)
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
		IntegrationUtil.deleteAll(QIdentityVerificationEntity.identityVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
