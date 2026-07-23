package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.hamcrest.Matchers.notNullValue

/**
 * `GET /users/v1/me/referral-code` E2E 테스트.
 * 추천 코드 lazy 발급(get-or-create)과 재호출 멱등성을 검증한다.
 */
class ReferralCodeE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/me/referral-code") {

		context("아직 추천 코드가 없는 유저가 조회하면") {
			it("A-Z0-9 8자 코드가 발급되어 저장·반환된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				val response = get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(userId))
				}
				response expect {
					status(200)
					body("success", true)
					body("data.referralCode", notNullValue())
				}
				val code: String = response.extract().path("data.referralCode")

				code shouldMatch Regex("^[A-Z0-9]{8}$")
				referralCodeOf(userId) shouldBe code
			}
		}

		context("이미 코드가 있는 유저가 다시 조회하면") {
			it("같은 코드를 그대로 반환한다") {
				val user = UserEntityFixture.create(status = UserStatus.ACTIVE)
				user.referralCode = "FIXED123"
				val userId: Long = IntegrationUtil.persist(user).id!!

				get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.referralCode", "FIXED123")
				}
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})

/** 저장된 추천 코드를 DB에서 직접 읽는다. */
internal fun referralCodeOf(userId: Long): String? =
	IntegrationUtil.getQuery()
		.select(QUserEntity.userEntity.referralCode)
		.from(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetchOne()
