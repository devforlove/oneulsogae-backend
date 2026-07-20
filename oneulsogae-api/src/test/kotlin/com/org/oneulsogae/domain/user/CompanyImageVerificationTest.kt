package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.domain.CompanyImageVerification
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [CompanyImageVerification] 도메인 유닛 테스트.
 * 제출은 PENDING으로 시작하고, 업로드 파일 검증(빈 파일·형식·크기)과 확장자 매핑을 확인한다.
 */
class CompanyImageVerificationTest : DescribeSpec({

	describe("create") {
		it("제출은 심사 대기(PENDING)로 시작하고 희망 회사명·이전 회사명을 담는다") {
			val verification: CompanyImageVerification =
				CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg", companyName = "오늘의 소개", previousCompanyName = "이전회사")

			verification.userId shouldBe 1L
			verification.imageKey shouldBe "k/1/a.jpg"
			verification.companyName shouldBe "오늘의 소개"
			verification.previousCompanyName shouldBe "이전회사"
			verification.status shouldBe CompanyImageVerificationStatus.PENDING
			verification.rejectionReason shouldBe null
		}

		it("이전 회사명이 없으면 null로 담는다") {
			val verification: CompanyImageVerification =
				CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg", companyName = "오늘의 소개", previousCompanyName = null)

			verification.previousCompanyName shouldBe null
		}

		it("imageKey가 비면 생성할 수 없다") {
			shouldThrow<IllegalArgumentException> {
				CompanyImageVerification.create(userId = 1L, imageKey = " ", companyName = "오늘의 소개", previousCompanyName = null)
			}
		}

		it("회사명이 비면 INVALID_COMPANY_NAME을 던진다") {
			val exception: BusinessException = shouldThrow<BusinessException> {
				CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg", companyName = " ", previousCompanyName = null)
			}
			exception.errorCode shouldBe UserErrorCode.INVALID_COMPANY_NAME
		}

		it("회사명이 50자를 넘으면 INVALID_COMPANY_NAME을 던진다") {
			val exception: BusinessException = shouldThrow<BusinessException> {
				CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg", companyName = "가".repeat(51), previousCompanyName = null)
			}
			exception.errorCode shouldBe UserErrorCode.INVALID_COMPANY_NAME
		}
	}

	describe("validateUpload") {
		it("허용 형식(JPEG·PNG·PDF)·정상 크기면 통과한다") {
			CompanyImageVerification.validateUpload("image/jpeg", 1_000)
			CompanyImageVerification.validateUpload("image/png", 1_000)
			CompanyImageVerification.validateUpload("application/pdf", 1_000)
		}

		it("빈 파일(size<=0)이면 EMPTY_IMAGE를 던진다") {
			shouldThrow<BusinessException> {
				CompanyImageVerification.validateUpload("image/jpeg", 0)
			}.errorCode shouldBe UserErrorCode.EMPTY_IMAGE
		}

		it("허용하지 않는 형식이면 INVALID_IMAGE_TYPE을 던진다") {
			shouldThrow<BusinessException> {
				CompanyImageVerification.validateUpload("image/gif", 1_000)
			}.errorCode shouldBe UserErrorCode.INVALID_IMAGE_TYPE
		}

		it("contentType이 null이면 INVALID_IMAGE_TYPE을 던진다") {
			shouldThrow<BusinessException> {
				CompanyImageVerification.validateUpload(null, 1_000)
			}.errorCode shouldBe UserErrorCode.INVALID_IMAGE_TYPE
		}

		it("10MB를 넘으면 IMAGE_TOO_LARGE를 던진다") {
			shouldThrow<BusinessException> {
				CompanyImageVerification.validateUpload("image/png", CompanyImageVerification.MAX_FILE_SIZE_BYTES + 1)
			}.errorCode shouldBe UserErrorCode.IMAGE_TOO_LARGE
		}
	}

	describe("extensionOf") {
		it("콘텐츠 타입을 확장자로 매핑한다") {
			CompanyImageVerification.extensionOf("image/jpeg") shouldBe "jpg"
			CompanyImageVerification.extensionOf("image/png") shouldBe "png"
			CompanyImageVerification.extensionOf("application/pdf") shouldBe "pdf"
		}
	}
})
