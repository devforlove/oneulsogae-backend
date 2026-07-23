package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.user.Gender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CoinUsageTypeTest : DescribeSpec({

	describe("coinAmount(gender)") {
		it("남성은 기존 금액을 그대로 낸다") {
			CoinUsageType.DATING_INIT.coinAmount(Gender.MALE) shouldBe 32
			CoinUsageType.MEETING_INIT.coinAmount(Gender.MALE) shouldBe 40
			CoinUsageType.DATING_ACCEPT.coinAmount(Gender.MALE) shouldBe 32
			CoinUsageType.MEETING_ACCEPT.coinAmount(Gender.MALE) shouldBe 40
			CoinUsageType.EXTRA_INTRO.coinAmount(Gender.MALE) shouldBe 30
			CoinUsageType.LOUNGE_CHAT_INIT.coinAmount(Gender.MALE) shouldBe 32
			CoinUsageType.LOUNGE_CHAT_ACCEPT.coinAmount(Gender.MALE) shouldBe 32
		}

		it("여성은 전부 절반을 낸다") {
			CoinUsageType.DATING_INIT.coinAmount(Gender.FEMALE) shouldBe 16
			CoinUsageType.MEETING_INIT.coinAmount(Gender.FEMALE) shouldBe 20
			CoinUsageType.DATING_ACCEPT.coinAmount(Gender.FEMALE) shouldBe 16
			CoinUsageType.MEETING_ACCEPT.coinAmount(Gender.FEMALE) shouldBe 20
			CoinUsageType.EXTRA_INTRO.coinAmount(Gender.FEMALE) shouldBe 15
			CoinUsageType.LOUNGE_CHAT_INIT.coinAmount(Gender.FEMALE) shouldBe 16
			CoinUsageType.LOUNGE_CHAT_ACCEPT.coinAmount(Gender.FEMALE) shouldBe 16
		}

		it("성별 미상(null)은 남성 금액으로 fallback한다") {
			CoinUsageType.DATING_INIT.coinAmount(null) shouldBe 32
		}
	}
})
