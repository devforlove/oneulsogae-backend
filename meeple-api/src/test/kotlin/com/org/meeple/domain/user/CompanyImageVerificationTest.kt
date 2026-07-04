package com.org.meeple.domain.user

import com.org.meeple.common.user.CompanyImageVerificationStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.domain.CompanyImageVerification
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [CompanyImageVerification] 도메인 유닛 테스트.
 * 제출은 PENDING으로 시작하고, 업로드 파일 검증(빈 파일·형식·크기)과 확장자 매핑을 확인한다.
 */
class CompanyImageVerificationTest : DescribeSpec({

	describe("create") {
		it("제출은 심사 대기(PENDING)로 시작한다") {
			val verification: CompanyImageVerification = CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg")

			verification.userId shouldBe 1L
			verification.imageKey shouldBe "k/1/a.jpg"
			verification.status shouldBe CompanyImageVerificationStatus.PENDING
		}

		it("imageKey가 비면 생성할 수 없다") {
			shouldThrow<IllegalArgumentException> {
				CompanyImageVerification.create(userId = 1L, imageKey = " ")
			}
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
