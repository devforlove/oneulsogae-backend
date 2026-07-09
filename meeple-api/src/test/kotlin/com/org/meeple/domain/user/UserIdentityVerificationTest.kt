package com.org.meeple.domain.user

import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.user.command.domain.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserIdentityVerificationTest : DescribeSpec({

	describe("create") {
		it("신규 가입 사용자는 본인확인 대기(IDENTITY_VERIFICATION_PENDING) 상태로 생성된다") {
			val user: User = User.create(provider = "kakao", providerId = "pid-1", email = "u@test.com")
			user.status shouldBe UserStatus.IDENTITY_VERIFICATION_PENDING
		}
	}

	describe("passIdentityVerification") {
		it("본인확인을 통과하면 온보딩(ONBOARDING) 상태로 전이한다") {
			val user: User = User.create(provider = "kakao", providerId = "pid-1", email = "u@test.com")
			user.passIdentityVerification().status shouldBe UserStatus.ONBOARDING
		}
	}
})
