package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUniversityImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.UniversityImageVerificationEntity
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.restassured.RestAssured

/**
 * `POST /users/v1/university-image/verifications` E2E 테스트. (멀티파트 업로드)
 * 학교 서류 이미지를 업로드하면 university_image_verifications에 오브젝트 키·PENDING이 저장되는지,
 * 잘못된 형식·비인증이 각각 막히는지 검증한다. (직장 서류 인증 E2E와 같은 구성)
 */
class SubmitUniversityImageVerificationE2ETest : AbstractIntegrationSupport({

	fun persistUser(providerId: String): Long =
		IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!

	fun latestVerificationOf(userId: Long): UniversityImageVerificationEntity? {
		val v: QUniversityImageVerificationEntity = QUniversityImageVerificationEntity.universityImageVerificationEntity
		return IntegrationUtil.getQuery().selectFrom(v).where(v.userId.eq(userId)).fetchFirst()
	}

	describe("POST /users/v1/university-image/verifications") {

		context("유효한 서류 이미지를 업로드하면") {
			it("university_image_verifications에 오브젝트 키·PENDING으로 저장하고 200을 반환한다") {
				val userId: Long = persistUser("university-image-1")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "enrollment.jpg", "fake-image-bytes".toByteArray(), "image/jpeg")
					.multiPart("universityName", "한국대학교", "text/plain;charset=UTF-8")
					.post("/users/v1/university-image/verifications")
					.then()
					.statusCode(200)
					.body("success", org.hamcrest.Matchers.equalTo(true))
					.body("data.status", org.hamcrest.Matchers.equalTo("PENDING"))

				val saved: UniversityImageVerificationEntity = latestVerificationOf(userId)!!
				saved.status shouldBe UniversityImageVerificationStatus.PENDING
				saved.imageKey.shouldNotBeNull()
				saved.imageKey shouldStartWith "university-image-verifications/$userId/"
				saved.universityName shouldBe "한국대학교"
			}
		}

		context("프로필에 학교명이 있는 유저가 업로드하면") {
			it("제출 시점의 프로필 학교명을 이전 학교명으로 스냅샷한다") {
				val userId: Long = persistUser("university-image-prev")
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, universityName = "이전대학교"))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "enrollment.jpg", "fake-image-bytes".toByteArray(), "image/jpeg")
					.multiPart("universityName", "한국대학교", "text/plain;charset=UTF-8")
					.post("/users/v1/university-image/verifications")
					.then()
					.statusCode(200)

				val saved: UniversityImageVerificationEntity = latestVerificationOf(userId)!!
				saved.universityName shouldBe "한국대학교"
				saved.previousUniversityName shouldBe "이전대학교"
			}
		}

		context("허용하지 않는 형식(gif)을 업로드하면") {
			it("400(USER-021)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("university-image-2")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "anim.gif", "gif-bytes".toByteArray(), "image/gif")
					.multiPart("universityName", "한국대학교")
					.post("/users/v1/university-image/verifications")
					.then()
					.statusCode(400)
					.body("error.code", org.hamcrest.Matchers.equalTo("USER-021"))

				latestVerificationOf(userId) shouldBe null
			}
		}

		context("학교명 없이 업로드하면") {
			it("400(USER-041)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("university-image-3")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "enrollment.jpg", "fake-image-bytes".toByteArray(), "image/jpeg")
					.post("/users/v1/university-image/verifications")
					.then()
					.statusCode(400)
					.body("error.code", org.hamcrest.Matchers.equalTo("USER-041"))

				latestVerificationOf(userId) shouldBe null
			}
		}

		context("인증 없이 업로드하면") {
			it("401을 반환한다") {
				RestAssured.given()
					.multiPart("image", "enrollment.jpg", "fake".toByteArray(), "image/jpeg")
					.multiPart("universityName", "한국대학교")
					.post("/users/v1/university-image/verifications")
					.then()
					.statusCode(401)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUniversityImageVerificationEntity.universityImageVerificationEntity)
		IntegrationUtil.deleteAll(com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(com.org.oneulsogae.infra.user.command.entity.QUserEntity.userEntity)
	}
})
