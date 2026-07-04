package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.user.CompanyImageVerificationStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.CompanyImageVerificationEntity
import com.org.meeple.infra.user.command.entity.QCompanyImageVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.restassured.RestAssured

/**
 * `POST /users/v1/company-image/verifications` E2E 테스트. (멀티파트 업로드)
 * 서류 이미지를 업로드하면 company_image_verifications에 오브젝트 키·PENDING이 저장되는지,
 * 잘못된 형식·비인증이 각각 막히는지 검증한다.
 * (실제 S3 업로드는 [com.org.meeple.common.config.TestFileStorageConfig]의 페이크로 대체 — 어댑터는 S3FileStorageAdapterIntegrationTest에서 검증)
 */
class SubmitCompanyImageVerificationE2ETest : AbstractIntegrationSupport({

	fun persistUser(providerId: String): Long =
		IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!

	fun latestVerificationOf(userId: Long): CompanyImageVerificationEntity? {
		val v: QCompanyImageVerificationEntity = QCompanyImageVerificationEntity.companyImageVerificationEntity
		return IntegrationUtil.getQuery().selectFrom(v).where(v.userId.eq(userId)).fetchFirst()
	}

	describe("POST /users/v1/company-image/verifications") {

		context("유효한 서류 이미지를 업로드하면") {
			it("company_image_verifications에 오브젝트 키·PENDING으로 저장하고 200을 반환한다") {
				val userId: Long = persistUser("company-image-1")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "resume.jpg", "fake-image-bytes".toByteArray(), "image/jpeg")
					.post("/users/v1/company-image/verifications")
					.then()
					.statusCode(200)
					.body("success", org.hamcrest.Matchers.equalTo(true))
					.body("data.status", org.hamcrest.Matchers.equalTo("PENDING"))

				val saved: CompanyImageVerificationEntity = latestVerificationOf(userId)!!
				saved.status shouldBe CompanyImageVerificationStatus.PENDING
				saved.imageKey.shouldNotBeNull()
				saved.imageKey shouldStartWith "company-image-verifications/$userId/"
			}
		}

		context("허용하지 않는 형식(gif)을 업로드하면") {
			it("400(USER-021)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("company-image-2")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "anim.gif", "gif-bytes".toByteArray(), "image/gif")
					.post("/users/v1/company-image/verifications")
					.then()
					.statusCode(400)
					.body("success", org.hamcrest.Matchers.equalTo(false))
					.body("error.code", org.hamcrest.Matchers.equalTo("USER-021"))

				latestVerificationOf(userId) shouldBe null
			}
		}

		context("빈 파일을 업로드하면") {
			it("400(USER-020)을 반환한다") {
				val userId: Long = persistUser("company-image-3")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "empty.png", ByteArray(0), "image/png")
					.post("/users/v1/company-image/verifications")
					.then()
					.statusCode(400)
					.body("error.code", org.hamcrest.Matchers.equalTo("USER-020"))
			}
		}

		context("인증 없이 업로드하면") {
			it("401을 반환한다") {
				RestAssured.given()
					.multiPart("image", "resume.jpg", "fake".toByteArray(), "image/jpeg")
					.post("/users/v1/company-image/verifications")
					.then()
					.statusCode(401)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCompanyImageVerificationEntity.companyImageVerificationEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
