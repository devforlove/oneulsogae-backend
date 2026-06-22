package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /teams/v1/{teamId}` E2E 테스트. (결성된 팀 해체·떠나기)
 * ACTIVE 팀의 구성원이 해체하면 팀이 소프트 삭제되어 활성 조회에서 사라진다.
 */
class DisbandTeamE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 결성(ACTIVE)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락)
	fun formedTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		val teamId: Long = post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	describe("DELETE /teams/v1/{teamId}") {

		context("ACTIVE 팀의 구성원이 해체하면") {
			it("팀이 비활성화되어 활성 조회에서 사라진다 (200)") {
				val ownerId = 4001L
				val invitedUserId = 4002L
				val teamId: Long = formedTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
				}

				allTeams().size shouldBe 0
			}
		}

		context("아직 INVITING인 팀에 해체를 호출하면") {
			it("409(TEAM-008)를 반환한다") {
				val ownerId = 4003L
				val invitedUserId = 4004L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val teamId: Long = post("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
				}.extract().path<Int>("data.teamId").toLong()

				delete("/teams/v1/$teamId") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(409)
					body("error.code", "TEAM-008")
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

private fun allTeams(): List<TeamEntity> {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().selectFrom(team).fetch()
}
