package com.org.meeple.domain.user

import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.domain.User
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [User] 도메인 유닛 테스트.
 * 본인확인 시작 가능 여부 등 계정 상태에 관한 도메인 규칙을 검증한다.
 */
class UserTest : DescribeSpec({

	fun userWith(status: UserStatus): User =
		User(provider = "google", providerId = "pid", status = status)

	describe("validateCanStartIdentityVerification") {
		it("ONBOARDING 상태면 통과한다") {
			shouldNotThrowAny {
				userWith(UserStatus.ONBOARDING).validateCanStartIdentityVerification()
			}
		}

		it("이미 온보딩을 지난 상태(ACTIVE)면 IDENTITY_VERIFICATION_NOT_ONBOARDING을 던진다") {
			val ex: BusinessException = shouldThrow {
				userWith(UserStatus.ACTIVE).validateCanStartIdentityVerification()
			}

			ex.errorCode shouldBe UserErrorCode.IDENTITY_VERIFICATION_NOT_ONBOARDING
		}
	}
})
