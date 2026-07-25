package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UniversityImageVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUniversityImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.UniversityImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /admin/v1/university-image-verifications/{id}/approve|reject` E2E 테스트.
 * 승인: 인증 status를 APPROVED로 바꾸고 유저 user_details.universityName을 기입값으로 확정.
 * 반려: status를 REJECTED로. 없는 id 404(UNIVERSITY-IMAGE-001), 공백 학교명 400.
 */
class AdminUniversityVerificationReviewE2ETest : AbstractIntegrationSupport({

	fun verificationById(id: Long): UniversityImageVerificationEntity {
		val v: QUniversityImageVerificationEntity = QUniversityImageVerificationEntity.universityImageVerificationEntity
		return IntegrationUtil.getQuery().selectFrom(v).where(v.id.eq(id)).fetchOne()!!
	}

	fun detailByUserId(userId: Long): UserDetailEntity {
		val d: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return IntegrationUtil.getQuery().selectFrom(d).where(d.userId.eq(userId)).fetchOne()!!
	}

	describe("POST /admin/v1/university-image-verifications/{id}/approve") {

		it("승인하면 status=APPROVED로 바꾸고 유저 학교명을 확정한다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "uiv-approve")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저", universityName = null))
			val id: Long = IntegrationUtil.persist(
				UniversityImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "approve-key",
					status = UniversityImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/university-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"universityName":"한국대학교"}""")
			} expect {
				status(200)
				body("success", true)
			}

			verificationById(id).status shouldBe UniversityImageVerificationStatus.APPROVED
			detailByUserId(userId).universityName shouldBe "한국대학교"
		}

		it("공백 학교명은 400으로 막는다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "uiv-blank")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
			val id: Long = IntegrationUtil.persist(
				UniversityImageVerificationEntityFixture.create(userId = userId, imageKey = "blank-key"),
			).id!!

			post("/admin/v1/university-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"universityName":"  "}""")
			} expect {
				status(400)
			}

			verificationById(id).status shouldBe UniversityImageVerificationStatus.PENDING
		}

		it("없는 인증은 404(UNIVERSITY-IMAGE-001)") {
			post("/admin/v1/university-image-verifications/99999999/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"universityName":"한국대학교"}""")
			} expect {
				status(404)
				body("error.code", "UNIVERSITY-IMAGE-001")
			}
		}
	}

	describe("POST /admin/v1/university-image-verifications/{id}/reject") {

		it("반려하면 status=REJECTED로 바꾸고 사유를 남긴다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "uiv-reject")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, universityName = null))
			val id: Long = IntegrationUtil.persist(
				UniversityImageVerificationEntityFixture.create(userId = userId, imageKey = "reject-key"),
			).id!!

			post("/admin/v1/university-image-verifications/$id/reject") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"reason":"서류가 흐릿해요"}""")
			} expect {
				status(200)
			}

			val rejected: UniversityImageVerificationEntity = verificationById(id)
			rejected.status shouldBe UniversityImageVerificationStatus.REJECTED
			rejected.rejectionReason shouldBe "서류가 흐릿해요"
			// 반려는 프로필을 바꾸지 않는다.
			detailByUserId(userId).universityName shouldBe null
		}

		it("일반 유저 토큰으로는 접근할 수 없다 (403)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "uiv-forbidden")).id!!
			val id: Long = IntegrationUtil.persist(
				UniversityImageVerificationEntityFixture.create(userId = userId, imageKey = "forbidden-key"),
			).id!!

			post("/admin/v1/university-image-verifications/$id/reject") {
				bearer(accessTokenFor(userId))
				jsonBody("""{"reason":"x"}""")
			} expect {
				status(403)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUniversityImageVerificationEntity.universityImageVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(com.org.oneulsogae.infra.user.command.entity.QUserEntity.userEntity)
	}
})
