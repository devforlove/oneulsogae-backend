package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.UserStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** [UserStatus] 불변식: 파기 종단 상태 WITHDRAWN은 정식가입·매칭 대상이 아니다. */
class UserStatusTest : DescribeSpec({

	describe("WITHDRAWN") {
		it("정식 가입 상태가 아니다") { UserStatus.WITHDRAWN.isRegistered() shouldBe false }
		it("매칭 대상이 아니다") { UserStatus.WITHDRAWN.isMatchable() shouldBe false }
	}
})
