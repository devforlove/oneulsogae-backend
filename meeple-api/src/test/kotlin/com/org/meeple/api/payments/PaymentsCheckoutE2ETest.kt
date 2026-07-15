package com.org.meeple.api.payments

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.core.user.command.domain.IdentityVerificationStatus
import com.org.meeple.infra.fixture.IdentityVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.QIdentityVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserEntity
import org.hamcrest.Matchers.nullValue

/**
 * `GET /payments/v1/checkout` E2E 테스트.
 *
 * 결제(체크아웃) 화면 진입 시 주문자 정보(실명·이메일·휴대폰)를 반환한다.
 * 실명은 최신 VERIFIED 본인인증 행에서, 이메일은 users, 휴대폰은 user_details에서 읽는다.
 * 주문자 정보 미비는 화면 진입을 막지 않으므로 null 필드로 반환한다(에러 아님).
 */
class PaymentsCheckoutE2ETest : AbstractIntegrationSupport({

	describe("GET /payments/v1/checkout") {

		context("본인인증을 완료한 사용자가 조회하면") {
			it("최신 VERIFIED 인증의 실명과 이메일·휴대폰 번호를 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-1", email = "orderer@test.com"),
				)
				val userId: Long = user.id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, phoneNumber = "01011112222"))
				// 재인증 이력: 과거 VERIFIED → 최신 VERIFIED → 진행 중(REQUESTED) 순으로 쌓인 상황.
				// 최신 VERIFIED 행("김미플")이 선택되어야 하고, REQUESTED 행(실명 없음)은 무시되어야 한다.
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

				get("/payments/v1/checkout") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.orderer.name", "김미플")
					body("data.orderer.email", "orderer@test.com")
					body("data.orderer.phoneNumber", "01011112222")
				}
			}
		}

		context("프로필·본인인증이 없는 사용자가 조회하면") {
			it("모든 주문자 필드를 null로 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-2", email = null),
				)

				get("/payments/v1/checkout") {
					bearer(accessTokenFor(user.id!!))
				} expect {
					status(200)
					body("success", true)
					body("data.orderer.name", nullValue())
					body("data.orderer.email", nullValue())
					body("data.orderer.phoneNumber", nullValue())
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/payments/v1/checkout") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QIdentityVerificationEntity.identityVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
