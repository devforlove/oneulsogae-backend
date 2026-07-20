package com.org.oneulsogae.api.payments
import io.kotest.core.annotation.Ignored

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringProductEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IdentityVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.PaymentMethodEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.oneulsogae.infra.payments.command.entity.QPaymentMethodEntity
import com.org.oneulsogae.infra.user.command.entity.QIdentityVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.infra.user.command.entity.UserEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDateTime

/**
 * `GET /payments/v1/checkout?productId=` E2E 테스트.
 *
 * 결제(체크아웃) 화면 진입 시 주문자 정보 + 상품(모임 일정) 정보 + 활성 결제수단 목록을 반환한다.
 * - 주문자: 실명(최신 VERIFIED 본인인증)·이메일(users)·휴대폰(user_details). 미비는 null 필드(에러 아님).
 * - 상품: 정가(price)와 서버 확정 실결제가(salePrice — 얼리버드 유효 시 얼리버드가, 소진 시 할인가, 그 외 정가), 매진은 soldOut 플래그(200).
 * - 결제수단: active만 displayOrder 순.
 * - 없는 productId는 404(GATHERING-006), 모임 없음/모집중 아님은 404(GATHERING-001), 일정 미매칭은 404(PAYMENTS-001).
 * (presigned URL은 TestFileStorageConfig의 페이크 — https://presigned.test/<imageKey>)
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
class PaymentsCheckoutE2ETest : AbstractIntegrationSupport({

	// 모집중 모임 + 일정 + 상품을 저장하고 (gatheringId, scheduleId, 남성 NORMAL productId)를 돌려준다.
	fun persistGatheringWithSchedule(
		earlyBirdDiscountRate: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		discountMaleFee: Int? = null,
		maleRemaining: Int = 4,
	): Triple<Long, Long, Long> {
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
				maleRemaining = maleRemaining,
				earlyBirdCapacity = earlyBirdCapacity,
				earlyBirdRemaining = earlyBirdRemaining,
			),
		).id!!
		val products: List<GatheringProductEntity> = GatheringProductEntityFixture.tierSet(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			maleFee = 10000,
			femaleFee = 8000,
			earlyBirdDiscountRate = earlyBirdDiscountRate,
			discountMaleFee = discountMaleFee,
		).map { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
		val productId: Long = products.first { product: GatheringProductEntity ->
			product.gender == Gender.MALE && product.type == GatheringProductType.NORMAL
		}.id!!
		return Triple(gatheringId, scheduleId, productId)
	}

	describe("GET /payments/v1/checkout") {

		context("본인인증 유저가 얼리버드가 유효한 일정을 조회하면") {
			it("주문자·상품(얼리버드 실결제가)·활성 결제수단만 순서대로 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-1", email = "orderer@test.com"),
				)
				val userId: Long = user.id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, phoneNumber = "01011112222"))
				// 재인증 이력: 과거 VERIFIED → 최신 VERIFIED → 진행 중(REQUESTED). 최신 VERIFIED("김오늘의 소개")가 선택되어야 한다.
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김과거"))
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김오늘의 소개"))
				IntegrationUtil.persist(
					IdentityVerificationEntityFixture.create(
						userId = userId,
						status = IdentityVerificationStatus.REQUESTED,
						realName = null,
						verifiedAt = null,
					),
				)
				// 얼리버드 30% 유효(remaining 5) → 남성 salePrice = 10000×0.7 = 7000.
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
				)
				// 활성 2건(순서 역순 저장으로 정렬 검증) + 비활성 1건(제외 검증).
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "KAKAO_PAY", name = "카카오페이", displayOrder = 2))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "BANK_TRANSFER", name = "무통장입금", displayOrder = 1))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "CARD", name = "카드", displayOrder = 3, active = false))

				get("/payments/v1/checkout?productId=$productId") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.orderer.userId", userId.toInt())
					body("data.orderer.name", "김오늘의 소개")
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
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
					earlyBirdRemaining = 0,
					discountMaleFee = 9000,
				)

				get("/payments/v1/checkout?productId=$productId") {
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
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule()

				get("/payments/v1/checkout?productId=$productId") {
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
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule(maleRemaining = 0)

				get("/payments/v1/checkout?productId=$productId") {
					bearer(accessTokenFor(9003L))
				} expect {
					status(200)
					body("data.product.soldOut", true)
				}
			}
		}

		context("없는 productId로 조회하면") {
			it("404 GATHERING-006을 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "checkout-404")).id!!

				get("/payments/v1/checkout?productId=999999") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "GATHERING-006")
				}
			}
		}

		context("모임 A에는 속하지만 다른 모임 B의 scheduleId를 가진 상품으로 조회하면") {
			it("404(PAYMENTS-001)를 반환한다") {
				val (gatheringAId: Long, _, _) = persistGatheringWithSchedule()
				val (_, scheduleBId: Long, _) = persistGatheringWithSchedule()
				val mismatchedProductId: Long = IntegrationUtil.persist(
					GatheringProductEntityFixture.create(gatheringId = gatheringAId, scheduleId = scheduleBId),
				).id!!

				get("/payments/v1/checkout?productId=$mismatchedProductId") {
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
				val productId: Long = IntegrationUtil.persist(
					GatheringProductEntityFixture.create(gatheringId = gatheringId),
				).id!!

				get("/payments/v1/checkout?productId=$productId") {
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
				val (gatheringId: Long, scheduleId: Long, productId: Long) = persistGatheringWithSchedule()
				IntegrationUtil.persist(PaymentMethodEntityFixture.create())

				get("/payments/v1/checkout?productId=$productId") {
					bearer(accessTokenFor(user.id!!))
				} expect {
					status(200)
					body("data.orderer.userId", user.id!!.toInt())
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
				get("/payments/v1/checkout?productId=1") {} expect {
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
