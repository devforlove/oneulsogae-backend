package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class IdentityVerificationTest : DescribeSpec({

	val today: LocalDate = LocalDate.of(2026, 7, 9)
	val at: LocalDateTime = LocalDateTime.of(2026, 7, 9, 12, 0)

	fun certified(birthday: LocalDate): CertifiedIdentity =
		CertifiedIdentity(
			realName = "홍길동", birthday = birthday, gender = Gender.MALE,
			phoneNumber = "01012345678", ci = "CI-VALUE", di = "DI-VALUE",
			foreigner = false, telecom = "SKT",
		)

	fun requested(): IdentityVerification =
		IdentityVerification.request(userId = 1L, ordrIdxx = "ORD-1", regCertKey = "REG-1")

	describe("CertifiedIdentity.isAdult") {
		it("만 19세 생일 당일이면 성인이다") {
			certified(LocalDate.of(2007, 7, 9)).isAdult(today) shouldBe true
		}
		it("만 19세 생일 하루 전이면 미성년이다") {
			certified(LocalDate.of(2007, 7, 10)).isAdult(today) shouldBe false
		}
	}

	describe("request") {
		it("REQUESTED 상태로 생성된다") {
			requested().status shouldBe IdentityVerificationStatus.REQUESTED
		}
	}

	describe("validateForConfirm") {
		it("regCertKey/ordrIdxx가 다르면 MISMATCH를 던진다") {
			val exception = shouldThrow<BusinessException> {
				requested().validateForConfirm(regCertKey = "REG-1", ordrIdxx = "OTHER")
			}
			exception.errorCode shouldBe UserErrorCode.IDENTITY_VERIFICATION_MISMATCH
		}
		it("이미 VERIFIED면 ALREADY_VERIFIED를 던진다") {
			val verified: IdentityVerification = requested().complete(certified(LocalDate.of(1996, 1, 1)), today, at)
			val exception = shouldThrow<BusinessException> {
				verified.validateForConfirm(regCertKey = "REG-1", ordrIdxx = "ORD-1")
			}
			exception.errorCode shouldBe UserErrorCode.IDENTITY_ALREADY_VERIFIED
		}
	}

	describe("complete") {
		it("성인이면 VERIFIED로 전이하고 검증값을 채운다") {
			val verified: IdentityVerification = requested().complete(certified(LocalDate.of(1996, 1, 1)), today, at)
			verified.status shouldBe IdentityVerificationStatus.VERIFIED
			verified.realName shouldBe "홍길동"
			verified.di shouldBe "DI-VALUE"
			verified.verifiedAt shouldBe at
		}
		it("미성년이면 IDENTITY_NOT_ADULT를 던진다") {
			val exception = shouldThrow<BusinessException> {
				requested().complete(certified(LocalDate.of(2010, 1, 1)), today, at)
			}
			exception.errorCode shouldBe UserErrorCode.IDENTITY_NOT_ADULT
		}
	}
})
