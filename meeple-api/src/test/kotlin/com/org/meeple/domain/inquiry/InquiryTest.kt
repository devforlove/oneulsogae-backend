package com.org.meeple.domain.inquiry

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.inquiry.InquiryErrorCode
import com.org.meeple.core.inquiry.command.domain.Inquiry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InquiryTest : DescribeSpec({

	describe("Inquiry.create") {

		it("정상 입력이면 PENDING 상태로 생성된다") {
			val inquiry: Inquiry = Inquiry.create(
				userId = 1L,
				category = InquiryCategory.ACCOUNT,
				email = "user@test.com",
				message = "로그인이 안 됩니다.",
			)

			inquiry.status shouldBe InquiryStatus.PENDING
			inquiry.answer shouldBe null
			inquiry.answeredAt shouldBe null
		}

		it("userId가 null이면 익명 문의로 생성된다") {
			val inquiry: Inquiry = Inquiry.create(
				userId = null,
				category = InquiryCategory.ETC,
				email = "guest@test.com",
				message = "비로그인 상태에서 보내는 문의입니다.",
			)

			inquiry.userId shouldBe null
			inquiry.status shouldBe InquiryStatus.PENDING
		}

		it("이메일 형식이 아니면 INVALID_EMAIL을 던진다") {
			val exception: BusinessException = shouldThrow {
				Inquiry.create(1L, InquiryCategory.ACCOUNT, "invalid-email", "정상적인 문의 내용입니다.")
			}

			exception.errorCode shouldBe InquiryErrorCode.INVALID_EMAIL
		}

		it("문의 내용이 10자 미만이면 MESSAGE_TOO_SHORT를 던진다") {
			val exception: BusinessException = shouldThrow {
				Inquiry.create(1L, InquiryCategory.ACCOUNT, "user@test.com", "012345678")
			}

			exception.errorCode shouldBe InquiryErrorCode.MESSAGE_TOO_SHORT
		}

		it("문의 내용이 1000자를 초과하면 MESSAGE_TOO_LONG을 던진다") {
			val exception: BusinessException = shouldThrow {
				Inquiry.create(1L, InquiryCategory.ACCOUNT, "user@test.com", "가".repeat(1001))
			}

			exception.errorCode shouldBe InquiryErrorCode.MESSAGE_TOO_LONG
		}

		it("경계값(10자·1000자)은 통과한다") {
			Inquiry.create(1L, InquiryCategory.ETC, "user@test.com", "가".repeat(10)).message.length shouldBe 10
			Inquiry.create(1L, InquiryCategory.ETC, "user@test.com", "가".repeat(1000)).message.length shouldBe 1000
		}
	}
})
