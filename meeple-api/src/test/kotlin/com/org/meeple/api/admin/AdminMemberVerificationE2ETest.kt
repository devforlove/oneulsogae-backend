package com.org.meeple.api.admin

import com.org.meeple.common.gathering.MemberVerificationStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.MemberVerificationEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.gathering.command.entity.GatheringProfileEntity
import com.org.meeple.infra.gathering.command.entity.MemberVerificationEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProfileEntity
import com.org.meeple.infra.gathering.command.entity.QMemberVerificationEntity
import com.org.meeple.infra.matchuser.command.entity.MatchUserEntity
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

	fun detailByUserId(userId: Long): UserDetailEntity {
		val d: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return IntegrationUtil.getQuery().selectFrom(d).where(d.userId.eq(userId)).fetchOne()!!
	}

	fun matchUserByUserId(userId: Long): MatchUserEntity {
		val m: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return IntegrationUtil.getQuery().selectFrom(m).where(m.userId.eq(userId)).fetchOne()!!
	}

	fun gatheringProfileByUserId(userId: Long): GatheringProfileEntity {
		val p: QGatheringProfileEntity = QGatheringProfileEntity.gatheringProfileEntity
		return IntegrationUtil.getQuery().selectFrom(p).where(p.userId.eq(userId)).fetchOne()!!
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

		it("승인하면 status=APPROVED로 바꾸고 회사명(user_details·match_user)·gathering_profile(직종·직장상세·나이·키)을 확정한다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mv-approve")).id!!
			// user_details: 회사명 null, 키 175, 생일(픽스처 기본 1996-01-01) → gathering_profile에 나이·키가 스냅샷된다.
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저", companyName = null, height = 175))
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, companyName = "이전회사"))
			val id: Long = IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(userId = userId, status = MemberVerificationStatus.PENDING),
			).id!!

			post("/admin/v1/member-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"미플","jobCategory":"IT·개발직","jobDetail":"미플 백엔드 개발자"}""")
			} expect {
				status(200)
				body("success", true)
			}

			verificationById(id).status shouldBe MemberVerificationStatus.APPROVED
			// 회사명만 user_details·match_user에 확정.
			val detail: UserDetailEntity = detailByUserId(userId)
			detail.companyName shouldBe "미플"
			matchUserByUserId(userId).companyName shouldBe "미플"
			// 직종·직장상세·나이·키는 gathering_profile에 저장(나이는 생일 스냅샷, 키는 user_details에서 가져옴).
			val profile: GatheringProfileEntity = gatheringProfileByUserId(userId)
			profile.jobCategory shouldBe "IT·개발직"
			profile.jobDetail shouldBe "미플 백엔드 개발자"
			profile.height shouldBe 175
			profile.age shouldNotBe null
		}

		it("필수 입력(회사명 등)이 비면 400이다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mv-approve-blank")).id!!
			val id: Long = IntegrationUtil.persist(
				MemberVerificationEntityFixture.create(userId = userId, status = MemberVerificationStatus.PENDING),
			).id!!

			post("/admin/v1/member-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"","jobCategory":"IT·개발직","jobDetail":"미플 백엔드 개발자"}""")
			} expect {
				status(400)
				body("success", false)
			}
		}

		it("없는 id면 404다 (MEMBER-VERIFICATION-001)") {
			post("/admin/v1/member-verifications/999999/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"미플","jobCategory":"IT·개발직","jobDetail":"미플 백엔드 개발자"}""")
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
		IntegrationUtil.deleteAll(QGatheringProfileEntity.gatheringProfileEntity)
		IntegrationUtil.deleteAll(QMemberVerificationEntity.memberVerificationEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
