package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AdminGatheringMemberTest : DescribeSpec({

	fun member(status: GatheringMemberStatus): AdminGatheringMember =
		AdminGatheringMember(
			id = 1L,
			userId = 7L,
			scheduleId = 100L,
			gender = Gender.MALE,
			status = status,
			earlyBirdApplied = false,
		)

	describe("validateApprovable / validateRejectable") {

		context("승인대기 상태면") {
			it("회원 인증된 유저는 승인 통과, 거절도 통과한다") {
				member(GatheringMemberStatus.PENDING).validateApprovable(memberVerified = true)
				member(GatheringMemberStatus.PENDING).validateRejectable()
			}

			it("회원 인증되지 않은 유저를 승인하면 GATHERING_MEMBER_NOT_VERIFIED를 던진다") {
				shouldThrow<AdminException> {
					member(GatheringMemberStatus.PENDING).validateApprovable(memberVerified = false)
				}.errorCode shouldBe AdminErrorCode.GATHERING_MEMBER_NOT_VERIFIED
			}
		}

		context("승인대기가 아닌 상태면") {
			it("인증 여부와 무관하게 GATHERING_MEMBER_INVALID_STATUS_TRANSITION을 던진다") {
				listOf(GatheringMemberStatus.JOINED, GatheringMemberStatus.REJECTED, GatheringMemberStatus.CANCELED)
					.forEach { status: GatheringMemberStatus ->
						val approveException: AdminException = shouldThrow<AdminException> {
							member(status).validateApprovable(memberVerified = true)
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
