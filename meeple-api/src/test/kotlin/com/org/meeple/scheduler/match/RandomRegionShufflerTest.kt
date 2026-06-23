package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.command.application.port.out.RandomRegionShuffler
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/**
 * [RandomRegionShuffler] 유닛 테스트.
 * "앞 10개만 섞고 11번째 이후는 순서 유지"를 구조 불변식으로 검증한다. (시드 고정으로 결정적)
 */
class RandomRegionShufflerTest : DescribeSpec({

	describe("shuffleNearest") {

		it("앞 10개는 같은 원소의 재배열, 11번째 이후는 순서를 유지한다") {
			val input: List<Long> = (1L..15L).toList()
			val shuffler = RandomRegionShuffler(Random(42))

			val result: List<Long> = shuffler.shuffleNearest(input)

			result.size shouldBe 15
			result.sorted() shouldBe input                 // 원소 보존(누락·중복 없음)
			result.drop(10) shouldBe input.drop(10)        // 11~15는 가까운 순 그대로
			result.take(10).sorted() shouldBe (1L..10L).toList() // 앞 10개는 자기들끼리의 순열
		}

		it("입력이 10개 이하이면 전체가 셔플 대상이고 원소를 보존한다") {
			val input: List<Long> = (1L..5L).toList()
			val shuffler = RandomRegionShuffler(Random(1))

			val result: List<Long> = shuffler.shuffleNearest(input)

			result.sorted() shouldBe input
		}

		it("입력이 1개 이하이면 그대로 반환한다") {
			val shuffler = RandomRegionShuffler(Random(1))

			shuffler.shuffleNearest(emptyList()) shouldBe emptyList()
			shuffler.shuffleNearest(listOf(7L)) shouldBe listOf(7L)
		}
	}
})
