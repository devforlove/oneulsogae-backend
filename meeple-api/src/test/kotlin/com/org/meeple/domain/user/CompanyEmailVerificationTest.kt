package com.org.meeple.domain.user

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.domain.CompanyEmailVerification
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [CompanyEmailVerification] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(회사 이메일 검증·생성)을 검증한다. 시각은 파라미터로 주입한다.
 */
class CompanyEmailVerificationTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val userId: Long = 1L
	val code: String = "123456"

	describe("create") {
		it("회사 도메인 이메일이면 만료 시각(now + CODE_TTL)으로 생성한다") {
			val verification: CompanyEmailVerification =
				CompanyEmailVerification.create(userId, "hong@mycompany.com", code, now)

			verification.companyEmail shouldBe "hong@mycompany.com"
			verification.expiresAt shouldBe now.plus(CompanyEmailVerification.CODE_TTL)
		}

		it("개인/무료 이메일 도메인이면 PERSONAL_EMAIL_NOT_ALLOWED를 던진다") {
			val exception: BusinessException = shouldThrow {
				CompanyEmailVerification.create(userId, "hong@gmail.com", code, now)
			}
			exception.errorCode shouldBe UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED
		}

		it("도메인은 대소문자를 무시하고 차단한다") {
			val exception: BusinessException = shouldThrow {
				CompanyEmailVerification.create(userId, "Hong@Naver.COM", code, now)
			}
			exception.errorCode shouldBe UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED
		}

		it("차단 목록의 모든 도메인을 거부한다") {
			CompanyEmailVerification.PERSONAL_EMAIL_DOMAINS.forEach { domain: String ->
				val exception: BusinessException = shouldThrow {
					CompanyEmailVerification.create(userId, "user@$domain", code, now)
				}
				exception.errorCode shouldBe UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED
			}
		}
	}
})
