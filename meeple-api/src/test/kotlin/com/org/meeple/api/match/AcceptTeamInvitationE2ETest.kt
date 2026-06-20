package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /teams/v1/{teamId}/acceptance` E2E 테스트. (초대 수락)
 * 초대받은 사용자가 수락하면 본인이 ACTIVE가 되고 전원 ACTIVE이므로 팀이 FORMED로 전이한다.
 */
class AcceptTeamInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 팀을 결성(초대)하고 teamId를 돌려준다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	describe("POST /teams/v1/{teamId}/acceptance") {

		context("초대받은 사용자가 수락하면") {
			it("본인이 ACTIVE가 되고 팀이 FORMED가 된다 (200)") {
				val ownerId = 2001L
				val invitedUserId = 2002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.status", TeamStatus.FORMED.name)
				}

				val members: List<TeamMemberEntity> = teamMembersOf(teamId)
				members.all { it.status == TeamMemberStatus.ACTIVE } shouldBe true
			}
		}

		context("초대받지 않은(이미 ACTIVE인) owner가 수락하면") {
			it("400(TEAM-007)을 반환한다") {
				val ownerId = 2003L
				val invitedUserId = 2004L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(400)
					body("error.code", "TEAM-007")
				}
			}
		}

		context("없는 팀을 수락하면") {
			it("404(TEAM-005)를 반환한다") {
				val userId = 2005L
				persistMatchUser(userId, Gender.MALE)

				post("/teams/v1/999999/acceptance") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("error.code", "TEAM-005")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

private fun teamMembersOf(teamId: Long): List<TeamMemberEntity> {
	val member: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
	return IntegrationUtil.getQuery().selectFrom(member).where(member.teamId.eq(teamId)).fetch()
}
