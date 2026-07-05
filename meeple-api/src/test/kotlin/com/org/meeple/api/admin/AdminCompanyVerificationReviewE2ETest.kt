package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.CompanyImageVerificationStatus
import com.org.meeple.infra.fixture.CompanyImageVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.CompanyImageVerificationEntity
import com.org.meeple.infra.user.command.entity.QCompanyImageVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /admin/v1/company-image-verifications/{id}/approve|reject` E2E 테스트.
 * 승인: 인증 status를 APPROVED로 바꾸고 유저 user_details.companyName을 기입값으로 확정.
 * 반려: status를 REJECTED로. 없는 id 404(COMPANY-IMAGE-001), 공백 회사명 400.
 */
class AdminCompanyVerificationReviewE2ETest : AbstractIntegrationSupport({

	fun verificationById(id: Long): CompanyImageVerificationEntity {
		val v: QCompanyImageVerificationEntity = QCompanyImageVerificationEntity.companyImageVerificationEntity
		return IntegrationUtil.getQuery().selectFrom(v).where(v.id.eq(id)).fetchOne()!!
	}

	fun detailByUserId(userId: Long): UserDetailEntity {
		val d: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return IntegrationUtil.getQuery().selectFrom(d).where(d.userId.eq(userId)).fetchOne()!!
	}

	describe("POST /admin/v1/company-image-verifications/{id}/approve") {

		it("승인하면 status=APPROVED로 바꾸고 유저 회사명을 확정한다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-approve")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저", companyName = null))
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "approve-key",
					status = CompanyImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/company-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"미플"}""")
			} expect {
				status(200)
				body("success", true)
			}

			verificationById(id).status shouldBe CompanyImageVerificationStatus.APPROVED
			detailByUserId(userId).companyName shouldBe "미플"
		}

		it("공백 회사명이면 400이다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-blank")).id!!
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "blank-key"),
			).id!!

			post("/admin/v1/company-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":""}""")
			} expect {
				status(400)
				body("success", false)
			}
		}

		it("회사명이 50자를 넘으면 400이다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-long")).id!!
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "long-key"),
			).id!!
			val tooLong: String = "가".repeat(51)

			post("/admin/v1/company-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"$tooLong"}""")
			} expect {
				status(400)
				body("success", false)
			}
		}

		it("없는 id면 404다 (COMPANY-IMAGE-001)") {
			post("/admin/v1/company-image-verifications/999999/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"미플"}""")
			} expect {
				status(404)
				body("error.code", "COMPANY-IMAGE-001")
			}
		}
	}

	describe("POST /admin/v1/company-image-verifications/{id}/reject") {

		it("반려하면 status=REJECTED로 바꾼다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-reject")).id!!
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "reject-key",
					status = CompanyImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/company-image-verifications/$id/reject") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
			}

			verificationById(id).status shouldBe CompanyImageVerificationStatus.REJECTED
		}

		it("반려 시 사유를 저장한다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-reject-reason")).id!!
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "reject-reason-key",
					status = CompanyImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/company-image-verifications/$id/reject") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"reason":"서류가 불명확합니다"}""")
			} expect {
				status(200)
				body("success", true)
			}

			val rejected: CompanyImageVerificationEntity = verificationById(id)
			rejected.status shouldBe CompanyImageVerificationStatus.REJECTED
			rejected.rejectionReason shouldBe "서류가 불명확합니다"
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCompanyImageVerificationEntity.companyImageVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
