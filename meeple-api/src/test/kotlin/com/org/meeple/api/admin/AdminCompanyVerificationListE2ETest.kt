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
import org.hamcrest.Matchers.hasSize

/**
 * `GET /admin/v1/company-image-verifications` E2E 테스트.
 * 직장 서류 인증을 최신순(id desc) 페이징 조회하고, status 필터·페이징 메타데이터,
 * user_details(nickname)·users(email) 조인, 열람용 imageUrl(테스트 페이크)이 채워지는지 검증한다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class AdminCompanyVerificationListE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/company-image-verifications") {

		it("최신순으로 페이징 조회하고 조인 정보·imageUrl을 채운다") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "civ-list", email = "civ@test.com"),
			).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저"))
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "key-pending",
					status = CompanyImageVerificationStatus.PENDING,
				),
			)
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "key-approved",
					status = CompanyImageVerificationStatus.APPROVED,
				),
			)

			get("/admin/v1/company-image-verifications") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순(id desc): 마지막에 저장한 APPROVED가 먼저.
				body("data.content[0].status", "APPROVED")
				body("data.content[0].statusLabel", "승인")
				body("data.content[0].imageUrl", "https://presigned.test/key-approved")
				body("data.content[0].userId", userId.toInt())
				body("data.content[0].nickname", "인증유저")
				body("data.content[0].email", "civ@test.com")
			}
		}

		it("status로 필터한다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-filter")).id!!
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "f-pending", status = CompanyImageVerificationStatus.PENDING),
			)
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "f-approved", status = CompanyImageVerificationStatus.APPROVED),
			)
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "f-rejected", status = CompanyImageVerificationStatus.REJECTED),
			)

			get("/admin/v1/company-image-verifications?status=PENDING") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content", hasSize<Any>(1))
				body("data.content[0].status", "PENDING")
				body("data.content[0].statusLabel", "심사 대기")
			}
		}

		it("size로 페이지 크기를 제한한다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-page")).id!!
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(
					CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "p-$index"),
				)
			}

			get("/admin/v1/company-image-verifications?page=0&size=2") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.content", hasSize<Any>(2))
				body("data.size", 2)
				body("data.totalElements", 3)
				body("data.totalPages", 2)
				body("data.hasNext", true)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCompanyImageVerificationEntity.companyImageVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
