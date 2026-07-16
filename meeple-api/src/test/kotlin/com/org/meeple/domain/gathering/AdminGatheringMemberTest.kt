package com.org.meeple.domain.gathering

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.gathering.command.domain.AdminGatheringMember
import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AdminGatheringMemberTest : DescribeSpec({

	fun member(status: GatheringMemberStatus): AdminGatheringMember =
		AdminGatheringMember(
			id = 1L,
			scheduleId = 100L,
			gender = Gender.MALE,
			status = status,
			earlyBirdApplied = false,
		)

	describe("validateApprovable / validateRejectable") {

		context("승인대기 상태면") {
			it("통과한다") {
				member(GatheringMemberStatus.PENDING).validateApprovable()
				member(GatheringMemberStatus.PENDING).validateRejectable()
			}
		}

		context("승인대기가 아닌 상태면") {
			it("GATHERING_MEMBER_INVALID_STATUS_TRANSITION을 던진다") {
				listOf(GatheringMemberStatus.JOINED, GatheringMemberStatus.REJECTED, GatheringMemberStatus.CANCELED)
					.forEach { status: GatheringMemberStatus ->
						val approveException: AdminException = shouldThrow<AdminException> {
							member(status).validateApprovable()
						}
						approveException.errorCode shouldBe AdminErrorCode.GATHERING_MEMBER_INVALID_STATUS_TRANSITION

						val rejectException: AdminException = shouldThrow<AdminException> {
							member(status).validateRejectable()
						}
						rejectException.errorCode shouldBe AdminErrorCode.GATHERING_MEMBER_INVALID_STATUS_TRANSITION
					}
			}
		}
	}
})
