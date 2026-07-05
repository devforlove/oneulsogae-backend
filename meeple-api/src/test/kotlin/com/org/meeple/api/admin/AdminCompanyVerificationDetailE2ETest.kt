package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.user.CompanyImageVerificationStatus
import com.org.meeple.infra.fixture.CompanyImageVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.QCompanyImageVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity

/**
 * `GET /admin/v1/company-image-verifications/{id}` E2E 테스트.
 * 직장 서류 인증 상세(목록 필드 + 주장 직장정보 + 열람용 imageUrl)를 200으로 반환하고,
 * 없는 id는 404(COMPANY-IMAGE-001)를 반환하는지 검증한다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class AdminCompanyVerificationDetailE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/company-image-verifications/{id}") {

		it("인증 상세를 반환한다 (200)") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "civ-detail", email = "civd@test.com"),
			).id!!
			IntegrationUtil.persist(
				UserDetailEntityFixture.create(
					userId = userId,
					nickname = "인증유저",
					job = "백엔드 개발자",
					companyEmail = "hr@meeple.com",
					companyName = "미플",
				),
			)
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "detail-key",
					status = CompanyImageVerificationStatus.APPROVED,
				),
			).id!!

			get("/admin/v1/company-image-verifications/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.id", id.toInt())
				body("data.userId", userId.toInt())
				body("data.status", "APPROVED")
				body("data.statusLabel", "승인")
				body("data.nickname", "인증유저")
				body("data.email", "civd@test.com")
				body("data.companyName", "미플")
				body("data.companyEmail", "hr@meeple.com")
				body("data.job", "백엔드 개발자")
				body("data.imageUrl", "https://presigned.test/detail-key")
			}
		}

		it("제출 희망 회사명과 반려 사유를 노출한다") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "civ-detail-extra", email = "cde@test.com"),
			).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저"))
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "extra-key",
					status = CompanyImageVerificationStatus.REJECTED,
					companyName = "지원회사",
					rejectionReason = "서류 재제출 필요",
				),
			).id!!

			get("/admin/v1/company-image-verifications/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.requestedCompanyName", "지원회사")
				body("data.rejectionReason", "서류 재제출 필요")
			}
		}

		it("없는 id면 404다 (COMPANY-IMAGE-001)") {
			get("/admin/v1/company-image-verifications/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "COMPANY-IMAGE-001")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCompanyImageVerificationEntity.companyImageVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
