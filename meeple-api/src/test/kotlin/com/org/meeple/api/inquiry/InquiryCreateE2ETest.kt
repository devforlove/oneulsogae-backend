package com.org.meeple.api.inquiry

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.inquiry.command.entity.InquiryEntity
import com.org.meeple.infra.inquiry.command.entity.QInquiryEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.notNullValue

class InquiryCreateE2ETest : AbstractIntegrationSupport({

	describe("POST /inquiries/v1") {

		context("로그인 사용자가 유효한 문의를 보내면") {
			it("PENDING 상태로 저장되고 inquiryId를 반환한다 (200)") {
				val userId = 1001L

				val response = post("/inquiries/v1") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"category": "ACCOUNT", "email": "user@test.com", "message": "로그인이 안 됩니다. 도와주세요."}""")
				}
				response expect {
					status(200)
					body("success", true)
					body("data.inquiryId", notNullValue())
				}
				val inquiryId: Long = response.extract().path<Int>("data.inquiryId").toLong()

				val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
				val saved: InquiryEntity = IntegrationUtil.getQuery()
					.selectFrom(inquiry)
					.where(inquiry.userId.eq(userId))
					.fetchOne()!!
				saved.id shouldBe inquiryId
				saved.status shouldBe InquiryStatus.PENDING
				saved.category shouldBe InquiryCategory.ACCOUNT
				saved.email shouldBe "user@test.com"
			}
		}

		context("문의 내용이 10자 미만이면") {
			it("400을 반환한다") {
				post("/inquiries/v1") {
					bearer(accessTokenFor(1002L))
					jsonBody("""{"category": "ETC", "email": "user@test.com", "message": "짧음"}""")
				} expect {
					status(400)
					body("success", false)
				}
			}
		}

		context("인증 토큰이 없으면") {
				it("익명 문의로 저장되고(user_id NULL) inquiryId를 반환한다 (200)") {
					val response = post("/inquiries/v1") {
						jsonBody("""{"category": "ETC", "email": "guest@test.com", "message": "토큰 없이 보내는 문의입니다."}""")
					}
					response expect {
						status(200)
						body("success", true)
						body("data.inquiryId", notNullValue())
					}
					val inquiryId: Long = response.extract().path<Int>("data.inquiryId").toLong()

					val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
					val saved: InquiryEntity = IntegrationUtil.getQuery()
						.selectFrom(inquiry)
						.where(inquiry.id.eq(inquiryId))
						.fetchOne()!!
					saved.userId shouldBe null
					saved.status shouldBe InquiryStatus.PENDING
					saved.email shouldBe "guest@test.com"
				}
			}
	}

	afterTest {
		IntegrationUtil.deleteAll(QInquiryEntity.inquiryEntity)
	}
})
