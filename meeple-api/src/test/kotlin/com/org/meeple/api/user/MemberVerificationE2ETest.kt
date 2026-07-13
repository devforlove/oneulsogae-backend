package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.user.MemberVerificationStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MemberVerificationEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.MemberVerificationEntity
import com.org.meeple.infra.user.command.entity.QMemberVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.restassured.RestAssured

/**
 * `POST /users/v1/member-verifications`·`GET /users/v1/member-verifications/me` E2E 테스트. (멀티파트 업로드)
 * 직업 정보와 사진 3종(얼굴·신분증·서류)을 제출하면 member_verifications에 오브젝트 키 3개·PENDING이 저장되는지,
 * 잘못된 사진 형식·직업 정보가 각각 막히는지, 내 최신 제출 조회(없으면 data null)가 동작하는지 검증한다.
 * (실제 S3 업로드는 [com.org.meeple.common.config.TestFileStorageConfig]의 페이크로 대체)
 */
class MemberVerificationE2ETest : AbstractIntegrationSupport({

	fun persistUser(providerId: String): Long =
		IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!

	fun latestVerificationOf(userId: Long): MemberVerificationEntity? {
		val v: QMemberVerificationEntity = QMemberVerificationEntity.memberVerificationEntity
		return IntegrationUtil.getQuery().selectFrom(v).where(v.userId.eq(userId)).orderBy(v.id.desc()).fetchFirst()
	}

	describe("POST /users/v1/member-verifications") {

		context("직업 정보와 사진 3종을 제출하면") {
			it("member_verifications에 오브젝트 키 3개·직업 정보·PENDING으로 저장하고 200을 반환한다") {
				val userId: Long = persistUser("member-verification-1")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("faceImage", "face.jpg", "fake-face-bytes".toByteArray(), "image/jpeg")
					.multiPart("idCardImage", "id-card.png", "fake-id-card-bytes".toByteArray(), "image/png")
					.multiPart("documentImage", "badge.pdf", "fake-doc-bytes".toByteArray(), "application/pdf")
					.multiPart("jobCategory", "IT·개발직", "text/plain;charset=UTF-8")
					.multiPart("jobDetail", "미플 백엔드 개발자", "text/plain;charset=UTF-8")
					.post("/users/v1/member-verifications")
					.then()
					.statusCode(200)
					.body("success", org.hamcrest.Matchers.equalTo(true))
					.body("data.status", org.hamcrest.Matchers.equalTo("PENDING"))

				val saved: MemberVerificationEntity = latestVerificationOf(userId)!!
				saved.status shouldBe MemberVerificationStatus.PENDING
				saved.jobCategory shouldBe "IT·개발직"
				saved.jobDetail shouldBe "미플 백엔드 개발자"
				saved.faceImageKey shouldStartWith "member-verifications/$userId/"
				saved.idCardImageKey shouldStartWith "member-verifications/$userId/"
				saved.documentImageKey shouldStartWith "member-verifications/$userId/"
			}
		}

		context("허용하지 않는 형식(gif)의 얼굴 사진을 제출하면") {
			it("400(USER-032)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("member-verification-2")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("faceImage", "face.gif", "gif-bytes".toByteArray(), "image/gif")
					.multiPart("idCardImage", "id-card.png", "fake-id-card-bytes".toByteArray(), "image/png")
					.multiPart("documentImage", "badge.pdf", "fake-doc-bytes".toByteArray(), "application/pdf")
					.multiPart("jobCategory", "IT·개발직", "text/plain;charset=UTF-8")
					.multiPart("jobDetail", "미플 백엔드 개발자", "text/plain;charset=UTF-8")
					.post("/users/v1/member-verifications")
					.then()
					.statusCode(400)
					.body("success", org.hamcrest.Matchers.equalTo(false))
					.body("error.code", org.hamcrest.Matchers.equalTo("USER-032"))

				latestVerificationOf(userId) shouldBe null
			}
		}

		context("직장명/직종/직급을 공백으로 제출하면") {
			it("400(USER-033)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("member-verification-3")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("faceImage", "face.jpg", "fake-face-bytes".toByteArray(), "image/jpeg")
					.multiPart("idCardImage", "id-card.png", "fake-id-card-bytes".toByteArray(), "image/png")
					.multiPart("documentImage", "badge.pdf", "fake-doc-bytes".toByteArray(), "application/pdf")
					.multiPart("jobCategory", "IT·개발직", "text/plain;charset=UTF-8")
					.multiPart("jobDetail", " ", "text/plain;charset=UTF-8")
					.post("/users/v1/member-verifications")
					.then()
					.statusCode(400)
					.body("error.code", org.hamcrest.Matchers.equalTo("USER-033"))

				latestVerificationOf(userId) shouldBe null
			}
		}

		context("인증 없이 제출하면") {
			it("401을 반환한다") {
				RestAssured.given()
					.multiPart("faceImage", "face.jpg", "fake".toByteArray(), "image/jpeg")
					.multiPart("idCardImage", "id-card.png", "fake".toByteArray(), "image/png")
					.multiPart("documentImage", "badge.pdf", "fake".toByteArray(), "application/pdf")
					.multiPart("jobCategory", "IT·개발직")
					.multiPart("jobDetail", "미플 백엔드 개발자")
					.post("/users/v1/member-verifications")
					.then()
					.statusCode(401)
			}
		}
	}

	describe("GET /users/v1/member-verifications/me") {

		context("제출 이력이 있는 유저가 조회하면") {
			it("최신 제출 1건을 반환한다") {
				val userId: Long = persistUser("member-verification-me")
				IntegrationUtil.persist(MemberVerificationEntityFixture.create(userId = userId))
				IntegrationUtil.persist(
					MemberVerificationEntityFixture.create(
						userId = userId,
						jobCategory = "공무원",
						jobDetail = "행정직 7급",
						status = MemberVerificationStatus.REJECTED,
						rejectionReason = "서류가 흐릿해요.",
					),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/users/v1/member-verifications/me")
					.then()
					.statusCode(200)
					.body("success", org.hamcrest.Matchers.equalTo(true))
					.body("data.status", org.hamcrest.Matchers.equalTo("REJECTED"))
					.body("data.jobCategory", org.hamcrest.Matchers.equalTo("공무원"))
					.body("data.jobDetail", org.hamcrest.Matchers.equalTo("행정직 7급"))
					.body("data.rejectionReason", org.hamcrest.Matchers.equalTo("서류가 흐릿해요."))
			}
		}

		context("제출 이력이 없는 유저가 조회하면") {
			it("data null을 반환한다") {
				val userId: Long = persistUser("member-verification-none")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/users/v1/member-verifications/me")
					.then()
					.statusCode(200)
					.body("success", org.hamcrest.Matchers.equalTo(true))
					.body("data", org.hamcrest.Matchers.nullValue())
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMemberVerificationEntity.memberVerificationEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
