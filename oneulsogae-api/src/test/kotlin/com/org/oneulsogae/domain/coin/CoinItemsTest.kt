package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [CoinItems] 일급 컬렉션 유닛 테스트.
 * 기간 한정 상품 보유 판정과, 특정 시점 만료 오퍼 제거를 검증한다.
 */
class CoinItemsTest : DescribeSpec({

	val createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
	val now: LocalDateTime = LocalDateTime.of(2026, 1, 5, 0, 0)

	fun item(id: Long, validDays: Int?): CoinItem =
		CoinItem(id = id, coinAmount = 100, price = 12000, salePrice = 10000, validDays = validDays)

	describe("hasTimeLimitedOffer") {
		it("validDays가 있는 상품이 하나라도 있으면 true다") {
			CoinItems(listOf(item(1, null), item(2, 7))).hasTimeLimitedOffer() shouldBe true
		}
		it("전부 상시(validDays null)면 false다") {
			CoinItems(listOf(item(1, null), item(2, null))).hasTimeLimitedOffer() shouldBe false
		}
	}

	describe("activeOffersAt") {
		it("만료된 기간 한정 상품은 제거하고 상시·유효 상품은 남긴다") {
			// id=1 상시(유지), id=2 유효(가입+7일, now=1/5 이전 → 유지), id=3 만료(가입+2일, now=1/5 이후 → 제거)
			val items = CoinItems(listOf(item(1, null), item(2, 7), item(3, 2)))

			val result: CoinItems = items.activeOffersAt(createdAt, now)

			result.values.map { it.id } shouldBe listOf(1L, 2L)
		}
	}
})
