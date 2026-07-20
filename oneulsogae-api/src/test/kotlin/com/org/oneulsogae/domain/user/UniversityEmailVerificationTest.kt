package com.org.oneulsogae.domain.user

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.domain.CompanyEmailVerification
import com.org.oneulsogae.core.user.command.domain.UniversityEmailVerification
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [UniversityEmailVerification] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(학교 이메일 검증·생성)을 검증한다. 시각은 파라미터로 주입한다.
 */
class UniversityEmailVerificationTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val userId: Long = 1L
	val code: String = "123456"

	describe("create") {
		it("학교 도메인 이메일이면 만료 시각(now + CODE_TTL)으로 생성한다") {
			val verification: UniversityEmailVerification =
				UniversityEmailVerification.create(userId, "student@snu.ac.kr", code, now)

			verification.universityEmail shouldBe "student@snu.ac.kr"
			verification.expiresAt shouldBe now.plus(UniversityEmailVerification.CODE_TTL)
		}

		it("개인/무료 이메일 도메인이면 PERSONAL_EMAIL_NOT_ALLOWED를 던진다") {
			val exception: BusinessException = shouldThrow {
				UniversityEmailVerification.create(userId, "student@gmail.com", code, now)
			}
			exception.errorCode shouldBe UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED
		}

		it("도메인은 대소문자를 무시하고 차단한다") {
			val exception: BusinessException = shouldThrow {
				UniversityEmailVerification.create(userId, "Student@Naver.COM", code, now)
			}
			exception.errorCode shouldBe UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED
		}

		it("차단 목록의 모든 도메인을 거부한다") {
			CompanyEmailVerification.PERSONAL_EMAIL_DOMAINS.forEach { domain: String ->
				val exception: BusinessException = shouldThrow {
					UniversityEmailVerification.create(userId, "user@$domain", code, now)
				}
				exception.errorCode shouldBe UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED
			}
		}
	}

	describe("validate") {
		val expiresAt: LocalDateTime = now.plusMinutes(10)
		val verification: UniversityEmailVerification =
			UniversityEmailVerification(userId = userId, universityEmail = "student@snu.ac.kr", code = code, expiresAt = expiresAt)

		it("코드가 일치하고 미만료·미사용이면 통과한다") {
			verification.validate(code, now)
		}

		it("코드가 다르면 VERIFICATION_CODE_MISMATCH를 던진다") {
			val exception: BusinessException = shouldThrow { verification.validate("000000", now) }
			exception.errorCode shouldBe UserErrorCode.VERIFICATION_CODE_MISMATCH
		}

		it("이미 검증됐으면 VERIFICATION_ALREADY_VERIFIED를 던진다") {
			val exception: BusinessException = shouldThrow { verification.verify(now).validate(code, now) }
			exception.errorCode shouldBe UserErrorCode.VERIFICATION_ALREADY_VERIFIED
		}

		it("만료됐으면 VERIFICATION_EXPIRED를 던진다") {
			val exception: BusinessException = shouldThrow { verification.validate(code, expiresAt.plusSeconds(1)) }
			exception.errorCode shouldBe UserErrorCode.VERIFICATION_EXPIRED
		}
	}
})
