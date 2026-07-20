package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.core.teammatch.command.domain.TeamMember
import com.org.oneulsogae.core.teammatch.command.domain.TeamMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
                TeamMember(teamId = 0, userId = ownerId, status = TeamMemberStatus.ACTIVE),
                TeamMember(teamId = 0, userId = invitedId, status = TeamMemberStatus.INVITED),
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

    describe("activeMembers") {
        it("ACTIVE 구성원만(전체 객체) 돌려준다") {
            invitingMembers().activeMembers().map { it.userId } shouldBe listOf(ownerId)
            invitingMembers().accept(invitedId).activeMembers().map { it.userId } shouldContainExactlyInAnyOrder
                listOf(ownerId, invitedId)
        }
    }

    describe("activeMemberIds") {
        it("ACTIVE 구성원의 userId만 돌려준다") {
            invitingMembers().activeMemberIds() shouldBe listOf(ownerId)
            invitingMembers().accept(invitedId).activeMemberIds() shouldContainExactlyInAnyOrder listOf(ownerId, invitedId)
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

    describe("withTeamId") {
        it("모든 구성원에 teamId를 채운 새 컬렉션을 돌려준다") {
            val assigned: TeamMembers = invitingMembers().withTeamId(7L)

            assigned.values.all { member: TeamMember -> member.teamId == 7L } shouldBe true
            // 원본은 그대로(불변)
            invitingMembers().values.all { member: TeamMember -> member.teamId == 0L } shouldBe true
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

    describe("deactivate(userId)") {
        it("해당 구성원만 DEACTIVE + deletedAt으로 표시하고 나머지는 그대로 둔다") {
            val now: LocalDateTime = LocalDateTime.of(2026, 6, 20, 12, 0)
            val formed: TeamMembers = invitingMembers().accept(invitedId) // 전원 ACTIVE

            val deactivated: TeamMembers = formed.deactivate(invitedId, now)

            val left: TeamMember = deactivated.find(invitedId)!!
            left.status shouldBe TeamMemberStatus.DEACTIVE
            left.deletedAt shouldBe now
            val remaining: TeamMember = deactivated.find(ownerId)!!
            remaining.status shouldBe TeamMemberStatus.ACTIVE
            remaining.deletedAt shouldBe null
        }
    }

    describe("hasActiveMemberExcept") {
        it("해당 구성원을 제외하고 활성 구성원이 남으면 true, 없으면 false다") {
            val formed: TeamMembers = invitingMembers().accept(invitedId) // 전원 ACTIVE

            // 전원 ACTIVE → 한 명을 빼도 다른 한 명이 남는다.
            formed.hasActiveMemberExcept(invitedId) shouldBe true
            // owner만 ACTIVE(invited는 INVITED) → owner를 빼면 활성 구성원이 없다.
            invitingMembers().hasActiveMemberExcept(ownerId) shouldBe false
        }
    }
})
