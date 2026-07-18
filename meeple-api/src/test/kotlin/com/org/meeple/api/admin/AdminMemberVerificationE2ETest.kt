package com.org.meeple.api.admin

import com.org.meeple.common.gathering.MemberVerificationStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MemberVerificationEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.gathering.command.entity.MemberVerificationEntity
import com.org.meeple.infra.gathering.command.entity.QMemberVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasSize

/**
 * 어드민 멤버 인증 심사 E2E 테스트.
 * - GET /admin/v1/member-verifications: 최신순(id desc) 페이징·status 필터·조인(nickname/email) 조회.
 * - GET /admin/v1/member-verifications/{id}: 상세 + 사진 3종 열람 URL(테스트 페이크). 없으면 404(MEMBER-VERIFICATION-001).
 * - POST /admin/v1/member-verifications/{id}/approve: status를 APPROVED로. 없으면 404.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class AdminMemberVerificationE2ETest : AbstractIntegrationSupport({

	fun verificationById(id: Long): MemberVerificationEntity {
		val v: QMemberVerificationEntity = QMemberVerificationEntity.memberVerificationEntity
		return IntegrationUtil.getQuery().selectFrom(v).where(v.id.eq(id)).fetchOne()!!
	}

	describe("GET /admin/v1/member-verifications") {

		it("최신순으로 페이징 조회하고 조인 정보를 채운다") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "mv-list", email = "mv@test.com"),
			).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저"))
			IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(userId = userId, jobCategory = "IT·개발직", status = MemberVerificationStatus.PENDING),
			)
			IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(userId = userId, jobCategory = "공무원", status = MemberVerificationStatus.APPROVED),
			)

			get("/admin/v1/member-verifications") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순(id desc): 마지막에 저장한 APPROVED가 먼저.
				body("data.content[0].status", "APPROVED")
				body("data.content[0].statusLabel", "승인")
				body("data.content[0].jobCategory", "공무원")
				body("data.content[0].userId", userId.toInt())
				body("data.content[0].nickname", "인증유저")
				body("data.content[0].email", "mv@test.com")
			}
		}

		it("status 필터로 해당 상태만 조회한다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mv-filter")).id!!
			IntegrationUtil.persist(MemberVerificationEntityFixture.create(userId = userId, status = MemberVerificationStatus.PENDING))
			IntegrationUtil.persist(MemberVerificationEntityFixture.create(userId = userId, status = MemberVerificationStatus.APPROVED))

			get("/admin/v1/member-verifications?status=PENDING") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content", hasSize<Any>(1))
				body("data.content[0].status", "PENDING")
			}
		}
	}

	describe("GET /admin/v1/member-verifications/{id}") {

		it("상세를 조회하고 사진 3종 열람 URL을 채운다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mv-detail")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저"))
			val id: Long = IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(
					userId = userId,
					jobCategory = "IT·개발직",
					jobDetail = "미플 백엔드 개발자",
					faceImageKey = "member-verifications/$userId/face.jpg",
					idCardImageKey = "member-verifications/$userId/id-card.jpg",
					documentImageKey = "member-verifications/$userId/doc.pdf",
					status = MemberVerificationStatus.PENDING,
				),
			).id!!

			get("/admin/v1/member-verifications/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.status", "PENDING")
				body("data.jobCategory", "IT·개발직")
				body("data.jobDetail", "미플 백엔드 개발자")
				body("data.nickname", "인증유저")
				body("data.faceImageUrl", "https://presigned.test/member-verifications/$userId/face.jpg")
				body("data.idCardImageUrl", "https://presigned.test/member-verifications/$userId/id-card.jpg")
				body("data.documentImageUrl", "https://presigned.test/member-verifications/$userId/doc.pdf")
			}
		}

		it("없는 id면 404다 (MEMBER-VERIFICATION-001)") {
			get("/admin/v1/member-verifications/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("error.code", "MEMBER-VERIFICATION-001")
			}
		}
	}

	describe("POST /admin/v1/member-verifications/{id}/approve") {

		it("승인하면 status=APPROVED로 바꾼다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mv-approve")).id!!
			val id: Long = IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(userId = userId, status = MemberVerificationStatus.PENDING),
			).id!!

			post("/admin/v1/member-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
			}

			verificationById(id).status shouldBe MemberVerificationStatus.APPROVED
		}

		it("없는 id면 404다 (MEMBER-VERIFICATION-001)") {
			post("/admin/v1/member-verifications/999999/approve") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("error.code", "MEMBER-VERIFICATION-001")
			}
		}
	}

	describe("POST /admin/v1/member-verifications/{id}/reject") {

		it("반려하면 status=REJECTED로 바꾸고 사유를 저장한다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mv-reject")).id!!
			val id: Long = IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(userId = userId, status = MemberVerificationStatus.PENDING),
			).id!!

			post("/admin/v1/member-verifications/$id/reject") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"reason":"서류가 흐릿합니다"}""")
			} expect {
				status(200)
				body("success", true)
			}

			val rejected: MemberVerificationEntity = verificationById(id)
			rejected.status shouldBe MemberVerificationStatus.REJECTED
			rejected.rejectionReason shouldBe "서류가 흐릿합니다"
		}

		it("사유 없이도 반려할 수 있다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mv-reject-noreason")).id!!
			val id: Long = IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(userId = userId, status = MemberVerificationStatus.PENDING),
			).id!!

			post("/admin/v1/member-verifications/$id/reject") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
			}

			verificationById(id).status shouldBe MemberVerificationStatus.REJECTED
		}

		it("없는 id면 404다 (MEMBER-VERIFICATION-001)") {
			post("/admin/v1/member-verifications/999999/reject") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("error.code", "MEMBER-VERIFICATION-001")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMemberVerificationEntity.memberVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
