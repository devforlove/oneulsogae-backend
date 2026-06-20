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
 * `DELETE /teams/v1/{teamId}/invitation` E2E 테스트. (초대 거절·취소)
 * INVITING 팀을 철회하면 팀이 소프트 삭제되어 활성 조회에서 사라진다.
 */
class WithdrawTeamInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	describe("DELETE /teams/v1/{teamId}/invitation") {

		context("초대받은 사람이 거절하면") {
			it("팀이 비활성화되어 활성 조회에서 사라진다 (200)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
				}

				allTeams().size shouldBe 0
			}
		}

		context("초대자(owner)가 취소하면") {
			it("팀이 비활성화된다 (200)") {
				val ownerId = 3003L
				val invitedUserId = 3004L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
				}

				allTeams().size shouldBe 0
			}
		}

		context("구성원이 아닌 사용자가 철회하면") {
			it("403(TEAM-006)을 반환한다") {
				val ownerId = 3005L
				val invitedUserId = 3006L
				val strangerId = 3007L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				persistMatchUser(strangerId, Gender.MALE)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("error.code", "TEAM-006")
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

// 소프트 삭제되지 않은(활성) 팀 전체. @SQLRestriction이 삭제행을 제외한다.
private fun allTeams(): List<TeamEntity> {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().selectFrom(team).fetch()
}
