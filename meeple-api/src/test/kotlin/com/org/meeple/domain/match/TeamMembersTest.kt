package com.org.meeple.domain.match

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.domain.TeamMember
import com.org.meeple.core.match.command.domain.TeamMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [TeamMembers] 일급 컬렉션 행위 유닛 테스트.
 */
class TeamMembersTest : DescribeSpec({

    val ownerId: Long = 1L
    val invitedId: Long = 2L

    fun invitingMembers(): TeamMembers =
        TeamMembers(
            listOf(
                TeamMember(teamId = 0, userId = ownerId, gender = Gender.MALE, status = TeamMemberStatus.ACTIVE),
                TeamMember(teamId = 0, userId = invitedId, gender = Gender.MALE, status = TeamMemberStatus.INVITED),
            ),
        )

    describe("find") {
        it("userId로 구성원을 찾고, 없으면 null이다") {
            invitingMembers().find(invitedId).shouldNotBeNull().userId shouldBe invitedId
            invitingMembers().find(999L) shouldBe null
        }
    }

    describe("inviterId / invitedId") {
        it("ACTIVE 구성원을 초대자, INVITED 구성원을 초대 대상으로 돌려준다") {
            invitingMembers().inviterId() shouldBe ownerId
            invitingMembers().invitedId() shouldBe invitedId
        }
    }

    describe("accept") {
        it("해당 구성원만 ACTIVE로 바꾸고 나머지는 그대로 둔다") {
            val accepted: TeamMembers = invitingMembers().accept(invitedId)

            accepted.find(invitedId)!!.status shouldBe TeamMemberStatus.ACTIVE
            accepted.find(ownerId)!!.status shouldBe TeamMemberStatus.ACTIVE
        }
    }

    describe("allActive") {
        it("전원 ACTIVE면 true, 하나라도 아니면 false다") {
            invitingMembers().allActive() shouldBe false
            invitingMembers().accept(invitedId).allActive() shouldBe true
        }
    }

    describe("deactivateAll") {
        it("전원을 DEACTIVE + deletedAt으로 표시한다") {
            val now: LocalDateTime = LocalDateTime.of(2026, 6, 20, 12, 0)

            val deactivated: TeamMembers = invitingMembers().deactivateAll(now)

            deactivated.values.forEach { member: TeamMember ->
                member.status shouldBe TeamMemberStatus.DEACTIVE
                member.deletedAt shouldBe now
            }
        }
    }
})
