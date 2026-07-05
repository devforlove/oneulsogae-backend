package com.org.meeple.api.admin

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.InquiryEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.inquiry.command.entity.QInquiryEntity
import org.hamcrest.Matchers.hasSize

/**
 * 어드민 문의 API E2E 테스트.
 * - GET /admin/v1/inquiries: 최신순 페이징, status 필터. 목록 행은 id/category/status/email/createdAt만.
 * - GET /admin/v1/inquiries/{id}: 상세(본문·답변 포함), 없는 id 404(INQUIRY-001).
 */
class AdminInquiryE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/inquiries") {

		it("최신순으로 페이징 조회하고 목록 행은 본문(message)을 제외한다") {
			IntegrationUtil.persist(
				InquiryEntityFixture.create(category = InquiryCategory.ACCOUNT, email = "a@test.com", message = "본문1"),
			)
			val lastId: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(category = InquiryCategory.PAYMENT, email = "b@test.com", message = "본문2"),
			).id!!

			get("/admin/v1/inquiries") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				body("data.content[0].id", lastId.toInt())
				body("data.content[0].category", "PAYMENT")
				body("data.content[0].status", "PENDING")
				body("data.content[0].email", "b@test.com")
				// 목록 행에 본문(message)은 없다.
				body("data.content[0].message", null)
			}
		}

		it("status로 상태를 필터한다") {
			IntegrationUtil.persist(InquiryEntityFixture.create(status = InquiryStatus.PENDING))
			IntegrationUtil.persist(InquiryEntityFixture.create(status = InquiryStatus.ANSWERED, answer = "답변함"))

			get("/admin/v1/inquiries?status=ANSWERED") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content", hasSize<Any>(1))
				body("data.content[0].status", "ANSWERED")
			}
		}

		it("size로 페이지 크기를 제한한다") {
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(InquiryEntityFixture.create(message = "문의-$index"))
			}

			get("/admin/v1/inquiries?page=0&size=2") {
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

	describe("GET /admin/v1/inquiries/{id}") {

		it("문의 상세를 본문·답변과 함께 반환한다 (200)") {
			val id: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(
					userId = 42L,
					category = InquiryCategory.MATCHING,
					email = "detail@test.com",
					message = "상세 본문",
				),
			).id!!

			get("/admin/v1/inquiries/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.userId", 42)
				body("data.category", "MATCHING")
				body("data.email", "detail@test.com")
				body("data.message", "상세 본문")
				body("data.status", "PENDING")
				body("data.answer", null)
			}
		}

		it("없는 id면 404다 (INQUIRY-001)") {
			get("/admin/v1/inquiries/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "INQUIRY-001")
			}
		}
	}

	describe("POST /admin/v1/inquiries/{id}/answer") {

		it("PENDING 문의에 답변하면 상세가 ANSWERED로 바뀐다 (200)") {
			val id: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(message = "답변 대상", status = InquiryStatus.PENDING),
			).id!!

			post("/admin/v1/inquiries/$id/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":"안녕하세요, 확인 후 답변드립니다."}""")
			} expect {
				status(200)
				body("success", true)
			}

			get("/admin/v1/inquiries/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.status", "ANSWERED")
				body("data.answer", "안녕하세요, 확인 후 답변드립니다.")
				body("data.answeredAt", org.hamcrest.Matchers.notNullValue())
			}
		}

		it("이미 답변된 문의면 409다 (INQUIRY-002)") {
			val id: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(status = InquiryStatus.ANSWERED, answer = "기존 답변"),
			).id!!

			post("/admin/v1/inquiries/$id/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":"두 번째 답변"}""")
			} expect {
				status(409)
				body("success", false)
				body("error.code", "INQUIRY-002")
			}
		}

		it("없는 id면 404다 (INQUIRY-001)") {
			post("/admin/v1/inquiries/999999/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":"답변"}""")
			} expect {
				status(404)
				body("error.code", "INQUIRY-001")
			}
		}

		it("답변이 비면 400이다") {
			val id: Long = IntegrationUtil.persist(InquiryEntityFixture.create()).id!!

			post("/admin/v1/inquiries/$id/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":""}""")
			} expect {
				status(400)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QInquiryEntity.inquiryEntity)
	}
})
