package com.org.meeple.domain.gathering

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode
import com.org.meeple.core.gathering.command.domain.Gathering
import com.org.meeple.core.gathering.command.domain.GatheringFee
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class GatheringTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
	val future: LocalDateTime = now.plusDays(1)
	val fee: GatheringFee = GatheringFee(male = 10000, female = 8000)

	describe("Gathering.create") {

		it("정상 입력이면 RECRUITING 상태로 생성된다") {
			val gathering: Gathering = Gathering.create(
				userId = 1L,
				type = GatheringType.PARTY,
				title = "주말 파티",
				description = "함께 즐겨요",
				region = "서울 강남구",
				gatheringAt = future,
				capacity = 4,
				fee = fee,
				earlyBirdFee = GatheringFee(male = 7000, female = 5000),
				discountFee = null,
				now = now,
			)

			gathering.status shouldBe GatheringStatus.RECRUITING
			gathering.type shouldBe GatheringType.PARTY
			gathering.userId shouldBe 1L
			gathering.region shouldBe "서울 강남구"
			gathering.fee shouldBe fee
			gathering.earlyBirdFee shouldBe GatheringFee(male = 7000, female = 5000)
			gathering.discountFee shouldBe null
		}

		it("userId가 null이면 운영 생성 모임으로 만들어진다") {
			val gathering: Gathering = Gathering.create(
				userId = null,
				type = GatheringType.COOKING,
				title = "쿠킹 클래스",
				description = null,
				region = "서울 마포구",
				gatheringAt = future,
				capacity = 6,
				fee = GatheringFee(male = 0, female = 0),
				earlyBirdFee = null,
				discountFee = null,
				now = now,
			)

			gathering.userId shouldBe null
			gathering.status shouldBe GatheringStatus.RECRUITING
		}

		it("제목이 공백이면 INVALID_TITLE을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "  ", null, "서울", future, 4, fee, null, null, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.INVALID_TITLE
		}

		it("제목이 100자를 초과하면 TITLE_TOO_LONG을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "가".repeat(101), null, "서울", future, 4, fee, null, null, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.TITLE_TOO_LONG
		}

		it("소개가 1000자를 초과하면 DESCRIPTION_TOO_LONG을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "파티", "가".repeat(1001), "서울", future, 4, fee, null, null, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.DESCRIPTION_TOO_LONG
		}

		it("지역이 공백이면 INVALID_REGION을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "파티", null, "  ", future, 4, fee, null, null, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.INVALID_REGION
		}

		it("정원이 2 미만이면 INVALID_CAPACITY를 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "파티", null, "서울", future, 1, fee, null, null, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.INVALID_CAPACITY
		}

		it("모임 일시가 현재와 같거나 이전이면 INVALID_GATHERING_AT을 던진다") {
			val exception: BusinessException = shouldThrow {
				Gathering.create(1L, GatheringType.PARTY, "파티", null, "서울", now, 4, fee, null, null, now)
			}

			exception.errorCode shouldBe GatheringErrorCode.INVALID_GATHERING_AT
		}

		it("경계값(정원 2·참가비 0·제목 100자·현재 직후 일시)은 통과한다") {
			val gathering: Gathering = Gathering.create(
				userId = 1L,
				type = GatheringType.ONE_ON_ONE_ROTATION,
				title = "가".repeat(100),
				description = "가".repeat(1000),
				region = "서울",
				gatheringAt = now.plusSeconds(1),
				capacity = 2,
				fee = GatheringFee(male = 0, female = 0),
				earlyBirdFee = null,
				discountFee = null,
				now = now,
			)

			gathering.capacity shouldBe 2
			gathering.fee shouldBe GatheringFee(male = 0, female = 0)
			gathering.title.length shouldBe 100
		}
	}

	describe("GatheringFee") {

		it("남/녀 참가비가 0원 이상이면 생성된다") {
			val created: GatheringFee = GatheringFee(male = 10000, female = 0)

			created.male shouldBe 10000
			created.female shouldBe 0
		}

		it("남성 참가비가 0 미만이면 INVALID_FEE를 던진다") {
			val exception: BusinessException = shouldThrow { GatheringFee(male = -1, female = 0) }

			exception.errorCode shouldBe GatheringErrorCode.INVALID_FEE
		}

		it("여성 참가비가 0 미만이면 INVALID_FEE를 던진다") {
			val exception: BusinessException = shouldThrow { GatheringFee(male = 0, female = -1) }

			exception.errorCode shouldBe GatheringErrorCode.INVALID_FEE
		}

		it("optional: 남/녀 둘 다 있으면 값 객체를 만든다") {
			GatheringFee.optional(male = 5000, female = 3000) shouldBe GatheringFee(male = 5000, female = 3000)
		}

		it("optional: 남/녀 둘 다 없으면 null이다") {
			GatheringFee.optional(male = null, female = null) shouldBe null
		}

		it("optional: 한쪽만 있으면 INVALID_FEE를 던진다") {
			shouldThrow<BusinessException> { GatheringFee.optional(male = 5000, female = null) }
				.errorCode shouldBe GatheringErrorCode.INVALID_FEE
			shouldThrow<BusinessException> { GatheringFee.optional(male = null, female = 3000) }
				.errorCode shouldBe GatheringErrorCode.INVALID_FEE
		}
	}
})
