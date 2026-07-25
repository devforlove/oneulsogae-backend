package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.domain.UniversityImageVerification
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [UniversityImageVerification] 도메인 유닛 테스트.
 * 생성(학교명 검증·이전 학교명 스냅샷)과 업로드 파일 검증 규칙이 도메인에 캡슐화됐는지 검증한다.
 */
class UniversityImageVerificationTest : DescribeSpec({

	describe("create") {

		context("학교명을 채워 제출하면") {
			it("PENDING 상태로 희망 학교명·이전 학교명 스냅샷을 담는다") {
				val verification: UniversityImageVerification = UniversityImageVerification.create(
					userId = 1L,
					imageKey = "university-image-verifications/1/a.jpg",
					universityName = "한국대학교",
					previousUniversityName = "이전대학교",
				)

				verification.status shouldBe UniversityImageVerificationStatus.PENDING
				verification.universityName shouldBe "한국대학교"
				verification.previousUniversityName shouldBe "이전대학교"
			}
		}

		context("학교명이 비었으면") {
			it("INVALID_UNIVERSITY_NAME 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					UniversityImageVerification.create(
						userId = 1L,
						imageKey = "university-image-verifications/1/a.jpg",
						universityName = "  ",
						previousUniversityName = null,
					)
				}
				exception.errorCode shouldBe UserErrorCode.INVALID_UNIVERSITY_NAME
			}
		}

		context("학교명이 최대 길이(50자)를 넘으면") {
			it("INVALID_UNIVERSITY_NAME 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					UniversityImageVerification.create(
						userId = 1L,
						imageKey = "university-image-verifications/1/a.jpg",
						universityName = "가".repeat(UniversityImageVerification.MAX_UNIVERSITY_NAME_LENGTH + 1),
						previousUniversityName = null,
					)
				}
				exception.errorCode shouldBe UserErrorCode.INVALID_UNIVERSITY_NAME
			}
		}
	}

	describe("validateUpload") {

		context("빈 파일이면") {
			it("EMPTY_IMAGE 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					UniversityImageVerification.validateUpload("image/jpeg", 0L)
				}
				exception.errorCode shouldBe UserErrorCode.EMPTY_IMAGE
			}
		}

		context("허용하지 않는 형식(gif)이면") {
			it("INVALID_IMAGE_TYPE 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					UniversityImageVerification.validateUpload("image/gif", 10L)
				}
				exception.errorCode shouldBe UserErrorCode.INVALID_IMAGE_TYPE
			}
		}

		context("최대 크기(10MB)를 넘으면") {
			it("IMAGE_TOO_LARGE 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					UniversityImageVerification.validateUpload("image/jpeg", UniversityImageVerification.MAX_FILE_SIZE_BYTES + 1)
				}
				exception.errorCode shouldBe UserErrorCode.IMAGE_TOO_LARGE
			}
		}

		context("허용 형식·크기면") {
			it("통과한다 (JPEG·PNG·PDF)") {
				UniversityImageVerification.validateUpload("image/jpeg", 10L)
				UniversityImageVerification.validateUpload("image/png", 10L)
				UniversityImageVerification.validateUpload("application/pdf", 10L)
			}
		}
	}
})
