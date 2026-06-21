package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue

/**
 * `GET /teams/v1/invitation` E2E 테스트. (내가 보낸 초대 현황 조회)
 * 요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 반환한다.
 * 초대받은 사람·비구성원·철회된 경우는 data=null(200)로 노출되지 않는다.
 */
class GetSentInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 팀을 결성(초대)하고 teamId를 돌려준다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	describe("GET /teams/v1/invitation") {

		context("초대자가 자신이 보낸 초대를 조회하면") {
			it("팀 메타와 구성원 현황(자신 ACTIVE, 대상 INVITED)을 반환한다 (200)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("success", true)
					body("data.teamId", teamId.toInt())
					body("data.name", "우리팀")
					body("data.introduction", "함께 즐겁게 활동할 팀이에요")
					body("data.status", TeamStatus.INVITING.name)
					body("data.members", hasSize<Any>(2))
					body("data.members.userId", containsInAnyOrder(ownerId.toInt(), invitedUserId.toInt()))
					body("data.members.status", containsInAnyOrder(TeamMemberStatus.ACTIVE.name, TeamMemberStatus.INVITED.name))
				}
			}
		}

		context("초대받은 유저가 조회하면") {
			it("data가 null이다 (200)") {
				val ownerId = 3003L
				val invitedUserId = 3004L
				inviteTeam(ownerId, invitedUserId)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("진행 중인 초대가 없는 유저가 조회하면") {
			it("data가 null이다 (200)") {
				val userId = 3005L
				persistMatchUser(userId, Gender.MALE)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("초대를 철회한 뒤 초대자가 조회하면") {
			it("data가 null이다 (200)") {
				val ownerId = 3006L
				val invitedUserId = 3007L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
				}

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/teams/v1/invitation") expect {
					status(401)
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
