package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.teammatch.command.domain.RecommendedTeamHistory
import com.org.oneulsogae.core.teammatch.command.domain.Team
import com.org.oneulsogae.core.teammatch.command.domain.TeamMember
import com.org.oneulsogae.core.teammatch.command.domain.TeamMembers
import com.org.oneulsogae.core.teammatch.command.domain.Teams
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * [Teams] 일급 컬렉션 행위 유닛 테스트.
 * 한 팀 매칭에 참가한 두 팀에 걸친 조회(행위자 팀 식별·상대 팀 구성원·전체 참가 구성원)를 검증한다.
 */
class TeamsTest : DescribeSpec({

	// teamId의 (userId, status) 구성원으로 ACTIVE 팀을 만든다.
	fun team(teamId: Long, vararg members: Pair<Long, TeamMemberStatus>): Team =
		Team(
			id = teamId,
			name = "팀$teamId",
			gender = Gender.MALE,
			regionId = 1L,
			members = TeamMembers(
				members.map { (userId: Long, status: TeamMemberStatus) ->
					TeamMember(teamId = teamId, userId = userId, status = status)
				},
			),
			status = TeamStatus.ACTIVE,
		)

	// 팀1: 1·2 (ACTIVE), 팀2: 3·4 (ACTIVE)
	val teams = Teams(
		listOf(
			team(1L, 1L to TeamMemberStatus.ACTIVE, 2L to TeamMemberStatus.ACTIVE),
			team(2L, 3L to TeamMemberStatus.ACTIVE, 4L to TeamMemberStatus.ACTIVE),
		),
	)

	describe("findByActiveMember") {
		it("userId가 ACTIVE 구성원으로 속한 팀을 찾는다") {
			teams.findByActiveMember(3L).shouldNotBeNull().id shouldBe 2L
		}

		it("어느 팀의 ACTIVE 구성원도 아니면 null이다") {
			teams.findByActiveMember(99L) shouldBe null
		}

		it("INVITED 구성원은 ACTIVE가 아니므로 찾지 못한다") {
			val inviting = Teams(listOf(team(1L, 1L to TeamMemberStatus.ACTIVE, 2L to TeamMemberStatus.INVITED)))
			inviting.findByActiveMember(2L) shouldBe null
		}
	}

	describe("activeMembers") {
		it("모든 팀의 ACTIVE 구성원을 소속 teamId와 함께 펼쳐 돌려준다") {
			teams.activeMembers().map { member: TeamMember -> member.userId to member.teamId } shouldContainExactlyInAnyOrder
				listOf(1L to 1L, 2L to 1L, 3L to 2L, 4L to 2L)
		}

		it("INVITED 구성원은 제외한다") {
			val mixed = Teams(listOf(team(1L, 1L to TeamMemberStatus.ACTIVE, 2L to TeamMemberStatus.INVITED)))
			mixed.activeMembers().map { member: TeamMember -> member.userId } shouldBe listOf(1L)
		}
	}

	describe("opponentActiveMemberIds") {
		it("행위자 팀을 제외한 상대 팀의 ACTIVE 구성원 userId만 돌려준다") {
			teams.opponentActiveMemberIds(1L) shouldContainExactlyInAnyOrder listOf(3L, 4L)
			teams.opponentActiveMemberIds(2L) shouldContainExactlyInAnyOrder listOf(1L, 2L)
		}
	}

	describe("matchHistories") {
		it("각 팀의 ACTIVE 구성원마다 (구성원 → 상대 팀 id) 이력을 만든다") {
			teams.matchHistories().map { history: RecommendedTeamHistory -> history.userId to history.teamId } shouldContainExactlyInAnyOrder
				listOf(1L to 2L, 2L to 2L, 3L to 1L, 4L to 1L)
		}

		it("INVITED 구성원은 제외한다") {
			val mixed = Teams(
				listOf(
					team(1L, 1L to TeamMemberStatus.ACTIVE, 2L to TeamMemberStatus.INVITED),
					team(2L, 3L to TeamMemberStatus.ACTIVE),
				),
			)
			mixed.matchHistories().map { history: RecommendedTeamHistory -> history.userId to history.teamId } shouldContainExactlyInAnyOrder
				listOf(1L to 2L, 3L to 1L)
		}
	}
})
