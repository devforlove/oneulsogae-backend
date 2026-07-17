package com.org.meeple.domain.gathering

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode
import com.org.meeple.core.gathering.command.domain.JoinPricing
import com.org.meeple.core.gathering.command.domain.JoiningSchedule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class JoiningScheduleTest : DescribeSpec({

	fun schedule(
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
		maleRemaining: Int = 4,
		femaleRemaining: Int = 4,
		earlyBirdRemaining: Int? = null,
		earlyBirdMaleFee: Int? = null,
		earlyBirdFemaleFee: Int? = null,
		discountMaleFee: Int? = null,
		discountFemaleFee: Int? = null,
	): JoiningSchedule =
		JoiningSchedule(
			id = 1L,
			gatheringId = 10L,
			status = status,
			maleFee = 10000,
			femaleFee = 8000,
			maleRemaining = maleRemaining,
			femaleRemaining = femaleRemaining,
			earlyBirdRemaining = earlyBirdRemaining,
			earlyBirdMaleFee = earlyBirdMaleFee,
			earlyBirdFemaleFee = earlyBirdFemaleFee,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
		)

	describe("register") {

		context("정가(NORMAL) 상품으로 신청하면") {
			it("정가로 접수하고 해당 성별 여분만 차감한다") {
				val target: JoiningSchedule = schedule()

				val pricing: JoinPricing = target.register(Gender.MALE, GatheringProductType.NORMAL)

				pricing.amount shouldBe 10000
				pricing.earlyBirdApplied shouldBe false
				target.maleRemaining shouldBe 3
				target.femaleRemaining shouldBe 4
			}
		}

		context("얼리버드(EARLY_BIRD) 상품으로 신청하고 얼리버드가 유효하면") {
			it("얼리버드가로 접수하고 얼리버드 여분도 차감한다") {
				val target: JoiningSchedule = schedule(earlyBirdRemaining = 2, earlyBirdFemaleFee = 5600)

				val pricing: JoinPricing = target.register(Gender.FEMALE, GatheringProductType.EARLY_BIRD)

				pricing.amount shouldBe 5600 // 저장된 얼리버드가
				pricing.earlyBirdApplied shouldBe true
				target.earlyBirdRemaining shouldBe 1
				target.femaleRemaining shouldBe 3
			}
		}

		context("얼리버드(EARLY_BIRD) 상품으로 신청했지만 얼리버드가 이미 소진되었으면") {
			it("GATHERING_EARLY_BIRD_SOLD_OUT을 던지고 여분을 차감하지 않는다") {
				val target: JoiningSchedule = schedule(
					earlyBirdRemaining = 0,
					earlyBirdMaleFee = 7000,
					discountMaleFee = 9000,
				)

				val exception: BusinessException =
					shouldThrow<BusinessException> { target.register(Gender.MALE, GatheringProductType.EARLY_BIRD) }

				exception.errorCode shouldBe GatheringErrorCode.GATHERING_EARLY_BIRD_SOLD_OUT
				target.maleRemaining shouldBe 4
				target.earlyBirdRemaining shouldBe 0
			}
		}

		context("할인가(DISCOUNT) 상품으로 신청하면") {
			it("할인가로 접수하고 얼리버드 여분은 차감하지 않는다") {
				val target: JoiningSchedule = schedule(
					earlyBirdRemaining = 0,
					earlyBirdMaleFee = 7000,
					discountMaleFee = 9000,
				)

				val pricing: JoinPricing = target.register(Gender.MALE, GatheringProductType.DISCOUNT)

				pricing.amount shouldBe 9000
				pricing.earlyBirdApplied shouldBe false
				target.earlyBirdRemaining shouldBe 0
				target.maleRemaining shouldBe 3
			}
		}

		context("예정 상태가 아닌 일정에 신청하면") {
			it("GATHERING_SCHEDULE_NOT_OPEN을 던지고 여분을 차감하지 않는다") {
				val target: JoiningSchedule = schedule(status = GatheringScheduleStatus.COMPLETED)

				val exception: BusinessException =
					shouldThrow<BusinessException> { target.register(Gender.MALE, GatheringProductType.NORMAL) }

				exception.errorCode shouldBe GatheringErrorCode.GATHERING_SCHEDULE_NOT_OPEN
				target.maleRemaining shouldBe 4
			}
		}

		context("해당 성별 여분이 없는 일정에 신청하면") {
			it("GATHERING_SOLD_OUT을 던진다") {
				val target: JoiningSchedule = schedule(maleRemaining = 0)

				val exception: BusinessException =
					shouldThrow<BusinessException> { target.register(Gender.MALE, GatheringProductType.NORMAL) }

				exception.errorCode shouldBe GatheringErrorCode.GATHERING_SOLD_OUT
			}
		}

		context("반대 성별 여분만 없는 일정에 신청하면") {
			it("정상 접수한다") {
				val target: JoiningSchedule = schedule(maleRemaining = 0, femaleRemaining = 1)

				val pricing: JoinPricing = target.register(Gender.FEMALE, GatheringProductType.NORMAL)

				pricing.amount shouldBe 8000
				target.femaleRemaining shouldBe 0
			}
		}
	}
})
