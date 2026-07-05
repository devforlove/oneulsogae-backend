package com.org.meeple.domain.inquiry

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.inquiry.command.domain.AdminInquiry
import com.org.meeple.common.inquiry.InquiryStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [AdminInquiry] 답변 도메인 유닛 테스트.
 * PENDING만 답변 가능(재답변 불허) 규칙을 검증한다.
 */
class AdminInquiryTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 7, 5, 10, 0)

	describe("answer") {
		it("PENDING이면 답변 값을 만든다") {
			val inquiry = AdminInquiry(id = 7L, status = InquiryStatus.PENDING)

			val answered = inquiry.answer(content = "답변 내용", now = now)

			answered.id shouldBe 7L
			answered.answer shouldBe "답변 내용"
			answered.answeredAt shouldBe now
		}

		it("이미 답변된(ANSWERED) 문의면 INQUIRY_ALREADY_ANSWERED를 던진다") {
			val inquiry = AdminInquiry(id = 7L, status = InquiryStatus.ANSWERED)

			val exception = shouldThrow<AdminException> {
				inquiry.answer(content = "답변 내용", now = now)
			}
			exception.errorCode shouldBe AdminErrorCode.INQUIRY_ALREADY_ANSWERED
		}
	}
})
