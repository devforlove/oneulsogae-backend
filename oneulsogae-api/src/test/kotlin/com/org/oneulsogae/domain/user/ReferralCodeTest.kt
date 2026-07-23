package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.domain.ReferralCode
import com.org.oneulsogae.core.user.command.domain.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.util.Random

class ReferralCodeTest : DescribeSpec({

	describe("ReferralCode.generate") {
		it("A-Z·0-9로 이루어진 8자 코드를 생성한다") {
			val code: String = ReferralCode.generate(Random(42L))
			code shouldMatch Regex("^[A-Z0-9]{8}$")
		}

		it("같은 시드면 같은 코드, 다른 시드면 다른 코드를 생성한다") {
			ReferralCode.generate(Random(1L)) shouldBe ReferralCode.generate(Random(1L))
			(ReferralCode.generate(Random(1L)) == ReferralCode.generate(Random(2L))) shouldBe false
		}
	}

	describe("User.canRefer") {
		val referrer = User(id = 10L, provider = "kakao", providerId = "p", status = UserStatus.ACTIVE)

		it("ACTIVE 추천인이 다른 유저를 추천하면 true") {
			referrer.canRefer(newUserId = 20L) shouldBe true
		}

		it("본인을 추천하면 false") {
			referrer.canRefer(newUserId = 10L) shouldBe false
		}

		it("ACTIVE가 아닌 추천인이면 false") {
			referrer.copy(status = UserStatus.ONBOARDING).canRefer(newUserId = 20L) shouldBe false
		}
	}
})
