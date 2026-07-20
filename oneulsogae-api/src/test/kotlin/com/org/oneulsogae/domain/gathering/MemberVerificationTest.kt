package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.common.gathering.MemberVerificationStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode
import com.org.oneulsogae.core.gathering.command.domain.MemberVerification
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MemberVerification] 도메인 유닛 테스트.
 * 제출은 PENDING으로 시작하고, 사진(얼굴·신분증)·서류 파일 검증(빈 파일·형식·크기)과
 * 직업 정보 검증(직종·직장명/직종/직급), 확장자 매핑을 확인한다.
 */
class MemberVerificationTest : DescribeSpec({

	describe("create") {
		it("제출은 심사 대기(PENDING)로 시작하고 직업 정보·사진 3종 키를 담는다") {
			val verification: MemberVerification = MemberVerification.create(
				userId = 1L,
				jobCategory = "IT·개발직",
				jobDetail = "오늘의 소개 백엔드 개발자",
				faceImageKey = "member-verifications/1/face.jpg",
				idCardImageKey = "member-verifications/1/id-card.jpg",
				documentImageKey = "member-verifications/1/doc.pdf",
			)

			verification.userId shouldBe 1L
			verification.jobCategory shouldBe "IT·개발직"
			verification.jobDetail shouldBe "오늘의 소개 백엔드 개발자"
			verification.faceImageKey shouldBe "member-verifications/1/face.jpg"
			verification.idCardImageKey shouldBe "member-verifications/1/id-card.jpg"
			verification.documentImageKey shouldBe "member-verifications/1/doc.pdf"
			verification.status shouldBe MemberVerificationStatus.PENDING
			verification.rejectionReason shouldBe null
		}

		it("이미지 키가 비면 생성할 수 없다") {
			shouldThrow<IllegalArgumentException> {
				MemberVerification.create(
					userId = 1L,
					jobCategory = "IT·개발직",
					jobDetail = "오늘의 소개 백엔드 개발자",
					faceImageKey = " ",
					idCardImageKey = "member-verifications/1/id-card.jpg",
					documentImageKey = "member-verifications/1/doc.pdf",
				)
			}
		}
	}

	describe("validatePhoto") {
		it("허용 형식(JPEG·PNG)·정상 크기면 통과한다") {
			MemberVerification.validatePhoto("image/jpeg", 1_000)
			MemberVerification.validatePhoto("image/png", 1_000)
		}

		it("허용하지 않는 형식(gif)이면 MEMBER_VERIFICATION_INVALID_PHOTO_TYPE을 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validatePhoto("image/gif", 1_000)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_PHOTO_TYPE
		}

		it("사진에 PDF는 허용하지 않는다") {
			shouldThrow<BusinessException> {
				MemberVerification.validatePhoto("application/pdf", 1_000)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_PHOTO_TYPE
		}

		it("contentType이 null이면 MEMBER_VERIFICATION_INVALID_PHOTO_TYPE을 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validatePhoto(null, 1_000)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_PHOTO_TYPE
		}

		it("빈 파일(size<=0)이면 MEMBER_VERIFICATION_EMPTY_FILE을 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validatePhoto("image/jpeg", 0)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_EMPTY_FILE
		}

		it("10MB를 넘으면 MEMBER_VERIFICATION_FILE_TOO_LARGE를 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validatePhoto("image/png", MemberVerification.MAX_FILE_SIZE_BYTES + 1)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_FILE_TOO_LARGE
		}
	}

	describe("validateDocument") {
		it("허용 형식(JPEG·PNG·PDF)·정상 크기면 통과한다") {
			MemberVerification.validateDocument("image/jpeg", 1_000)
			MemberVerification.validateDocument("image/png", 1_000)
			MemberVerification.validateDocument("application/pdf", 1_000)
		}

		it("허용하지 않는 형식(gif)이면 MEMBER_VERIFICATION_INVALID_DOCUMENT_TYPE을 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validateDocument("image/gif", 1_000)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_DOCUMENT_TYPE
		}

		it("빈 파일(size<=0)이면 MEMBER_VERIFICATION_EMPTY_FILE을 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validateDocument("application/pdf", 0)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_EMPTY_FILE
		}

		it("10MB를 넘으면 MEMBER_VERIFICATION_FILE_TOO_LARGE를 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validateDocument("application/pdf", MemberVerification.MAX_FILE_SIZE_BYTES + 1)
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_FILE_TOO_LARGE
		}
	}

	describe("validateJobInfo") {
		it("직종·직장명/직종/직급이 채워져 있으면 통과한다") {
			MemberVerification.validateJobInfo("IT·개발직", "오늘의 소개 백엔드 개발자")
		}

		it("직종이 공백이면 MEMBER_VERIFICATION_INVALID_JOB_INFO를 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validateJobInfo(" ", "오늘의 소개 백엔드 개발자")
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_JOB_INFO
		}

		it("직종이 30자를 넘으면 MEMBER_VERIFICATION_INVALID_JOB_INFO를 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validateJobInfo("가".repeat(31), "오늘의 소개 백엔드 개발자")
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_JOB_INFO
		}

		it("직장명/직종/직급이 공백이면 MEMBER_VERIFICATION_INVALID_JOB_INFO를 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validateJobInfo("IT·개발직", " ")
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_JOB_INFO
		}

		it("직장명/직종/직급이 100자를 넘으면 MEMBER_VERIFICATION_INVALID_JOB_INFO를 던진다") {
			shouldThrow<BusinessException> {
				MemberVerification.validateJobInfo("IT·개발직", "가".repeat(101))
			}.errorCode shouldBe GatheringErrorCode.MEMBER_VERIFICATION_INVALID_JOB_INFO
		}
	}

	describe("extensionOf") {
		it("콘텐츠 타입을 확장자로 매핑한다") {
			MemberVerification.extensionOf("image/jpeg") shouldBe "jpg"
			MemberVerification.extensionOf("image/png") shouldBe "png"
			MemberVerification.extensionOf("application/pdf") shouldBe "pdf"
		}
	}
})
