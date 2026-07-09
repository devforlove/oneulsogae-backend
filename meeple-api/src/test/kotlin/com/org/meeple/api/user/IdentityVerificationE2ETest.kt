package com.org.meeple.api.user

import com.org.meeple.common.config.FakeKcpCertData
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.user.command.domain.CertifiedIdentity
import com.org.meeple.core.user.command.domain.IdentityVerificationStatus
import com.org.meeple.infra.fixture.IdentityVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.notNullValue
import java.time.LocalDate

class IdentityVerificationE2ETest : AbstractIntegrationSupport({

	fun adult(di: String = "DI-UNIQUE"): CertifiedIdentity =
		CertifiedIdentity(
			realName = "홍길동", birthday = LocalDate.of(1996, 1, 1), gender = Gender.MALE,
			phoneNumber = "01012345678", ci = "CI-$di", di = di, foreigner = false, telecom = "SKT",
		)

	fun registerFor(userId: Long): Pair<String, String> {
		val response: ValidatableResponse = post("/users/v1/identity-verification/register") {
			bearer(accessTokenFor(userId))
		}
		response expect {
			status(200)
			body("data.callUrl", notNullValue())
			body("data.regCertKey", notNullValue())
			body("data.ordrIdxx", notNullValue())
		}
		val regCertKey: String = response.extract().path("data.regCertKey")
		val ordrIdxx: String = response.extract().path("data.ordrIdxx")
		return regCertKey to ordrIdxx
	}

	describe("POST /users/v1/identity-verification (register→confirm)") {

		context("성인이고 DI가 중복되지 않으면") {
			it("확정되고 사용자가 ONBOARDING으로 전이한다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (regCertKey: String, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult(di = "DI-OK")

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"$regCertKey","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(200)
					body("data.name", "홍길동")
					body("data.adult", true)
				}

				userStatusOfIdentity(userId) shouldBe UserStatus.ONBOARDING
				latestIdentityStatusOf(userId) shouldBe IdentityVerificationStatus.VERIFIED
			}
		}

		context("미성년이면") {
			it("IDENTITY_NOT_ADULT로 거절하고 상태를 유지한다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (regCertKey: String, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult().copy(birthday = LocalDate.of(2012, 1, 1))

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"$regCertKey","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-029")
				}

				userStatusOfIdentity(userId) shouldBe UserStatus.IDENTITY_VERIFICATION_PENDING
			}
		}

		context("같은 DI로 이미 가입한 다른 사용자가 있으면") {
			it("IDENTITY_ALREADY_REGISTERED로 거절한다 (409)") {
				val otherId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "other", status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(
					IdentityVerificationEntityFixture.create(userId = otherId, di = "DI-DUP"),
				)
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (regCertKey: String, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult(di = "DI-DUP")

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"$regCertKey","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(409)
					body("error.code", "USER-030")
				}
			}
		}

		context("거래 정보가 위변조되면") {
			it("IDENTITY_VERIFICATION_MISMATCH로 거절한다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.IDENTITY_VERIFICATION_PENDING),
				).id!!
				val (_, ordrIdxx: String) = registerFor(userId)
				FakeKcpCertData.next = adult()

				post("/users/v1/identity-verification/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"regCertKey":"TAMPERED","ordrIdxx":"$ordrIdxx"}""")
				} expect {
					status(400)
					body("error.code", "USER-027")
				}
			}
		}
	}

	afterTest {
		FakeKcpCertData.next = null
		cleanupIdentity()
	}
})
