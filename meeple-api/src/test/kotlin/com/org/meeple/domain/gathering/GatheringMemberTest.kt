package com.org.meeple.domain.gathering

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode
import com.org.meeple.core.gathering.command.domain.GatheringMember
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GatheringMemberTest : DescribeSpec({

	fun member(status: GatheringMemberStatus): GatheringMember =
		GatheringMember(
			id = 1L,
			gatheringId = 10L,
			scheduleId = 100L,
			userId = 1000L,
			gender = Gender.MALE,
			status = status,
			earlyBirdApplied = false,
		)

	describe("validateReRegistrable") {

		context("승인대기 상태면") {
			it("GATHERING_ALREADY_JOINED를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					member(GatheringMemberStatus.PENDING).validateReRegistrable()
				}
				exception.errorCode shouldBe GatheringErrorCode.GATHERING_ALREADY_JOINED
			}
		}

		context("참가 상태면") {
			it("GATHERING_ALREADY_JOINED를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					member(GatheringMemberStatus.JOINED).validateReRegistrable()
				}
				exception.errorCode shouldBe GatheringErrorCode.GATHERING_ALREADY_JOINED
			}
		}

		context("거절·참가취소 상태면") {
			it("통과한다") {
				member(GatheringMemberStatus.REJECTED).validateReRegistrable()
				member(GatheringMemberStatus.CANCELED).validateReRegistrable()
			}
		}
	}

	describe("revive") {

		context("거절 상태의 행을 되살리면") {
			it("승인대기로 전환하고 성별·얼리버드 적용 여부를 갱신한다") {
				val target: GatheringMember = member(GatheringMemberStatus.REJECTED)

				target.revive(gender = Gender.FEMALE, earlyBirdApplied = true)

				target.status shouldBe GatheringMemberStatus.PENDING
				target.gender shouldBe Gender.FEMALE
				target.earlyBirdApplied shouldBe true
			}
		}
	}

	describe("pending") {

		it("승인대기 상태의 신규 참가자를 생성한다") {
			val target: GatheringMember = GatheringMember.pending(
				gatheringId = 10L,
				scheduleId = 100L,
				userId = 1000L,
				gender = Gender.MALE,
				earlyBirdApplied = true,
			)

			target.id shouldBe null
			target.status shouldBe GatheringMemberStatus.PENDING
			target.earlyBirdApplied shouldBe true
		}
	}
})
