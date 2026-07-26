package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.common.coin.CoinSaleChannel
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** [CoinSaleChannel.sellableVia] 판정 규칙 유닛 테스트. BOTH는 PG·IAP 모두 판매 가능. */
class CoinSaleChannelTest : DescribeSpec({

	describe("sellableVia") {
		it("PG 상품은 PG로만 판매된다") {
			CoinSaleChannel.PG.sellableVia(CoinSaleChannel.PG) shouldBe true
			CoinSaleChannel.PG.sellableVia(CoinSaleChannel.IAP) shouldBe false
		}
		it("IAP 상품은 IAP로만 판매된다") {
			CoinSaleChannel.IAP.sellableVia(CoinSaleChannel.IAP) shouldBe true
			CoinSaleChannel.IAP.sellableVia(CoinSaleChannel.PG) shouldBe false
		}
		it("BOTH 상품은 PG·IAP 모두 판매된다") {
			CoinSaleChannel.BOTH.sellableVia(CoinSaleChannel.PG) shouldBe true
			CoinSaleChannel.BOTH.sellableVia(CoinSaleChannel.IAP) shouldBe true
		}
	}
})
