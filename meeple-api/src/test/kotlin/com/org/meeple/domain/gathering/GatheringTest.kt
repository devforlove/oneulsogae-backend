package com.org.meeple.domain.gathering

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode
import com.org.meeple.core.gathering.command.domain.Gathering
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class GatheringTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
	val future: LocalDateTime = now.plusDays(1)

	describe("Gathering.create") {

		it("정상 입력이면 RECRUITING 상태로 생성된다") {
			val gathering: Gathering = Gathering.create(
				userId = 1L,
				type = GatheringType.PARTY,
				title = "주말 파티",
				description = "함께 즐겨요",
				gatheringAt = future,
				capacity = 4,
				now = now,
			)

			gathering.status shouldBe GatheringStatus.RECRUITING
			gathering.type shouldBe GatheringType.PARTY
			gathering.userId shouldBe 1L
		}

		it("userId가 null이면 운영 생성 모임으로 만들어진다") {
			val gathering: Gathering = Gathering.create(
				userId = null,
				type = GatheringType.COOKING,
				title = "쿠킹 클래스",
				description = null,
				gatheringAt = future,
				capacity = 6,
				now = now,
			)

			gathering.userId shouldBe null
			gathering.status shouldBe GatheringStatus.RECRUITING
		}

		it("제목이 공백이면 INVALID_TITLE을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "  ", null, future, 4, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.INVALID_TITLE
		}

		it("제목이 100자를 초과하면 TITLE_TOO_LONG을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "가".repeat(101), null, future, 4, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.TITLE_TOO_LONG
		}

		it("소개가 1000자를 초과하면 DESCRIPTION_TOO_LONG을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "파티", "가".repeat(1001), future, 4, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.DESCRIPTION_TOO_LONG
		}

		it("정원이 2 미만이면 INVALID_CAPACITY를 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "파티", null, future, 1, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.INVALID_CAPACITY
		}

		it("모임 일시가 현재와 같거나 이전이면 INVALID_GATHERING_AT을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "파티", null, now, 4, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.INVALID_GATHERING_AT
		}

		it("경계값(정원 2·제목 100자·현재 직후 일시)은 통과한다") {
			val gathering: Gathering = Gathering.create(
				userId = 1L,
				type = GatheringType.ONE_ON_ONE_ROTATION,
				title = "가".repeat(100),
				description = "가".repeat(1000),
				gatheringAt = now.plusSeconds(1),
				capacity = 2,
				now = now,
			)

			gathering.capacity shouldBe 2
			gathering.title.length shouldBe 100
		}
	}
})
