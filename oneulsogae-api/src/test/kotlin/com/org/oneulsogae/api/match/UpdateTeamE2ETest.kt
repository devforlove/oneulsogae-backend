package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.integration.put
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity

/**
 * `PUT /teams/v1/{teamId}` E2E 테스트. (팀 이름·소개·활동지역 수정)
 * 진행 중(INVITING)이거나 결성(ACTIVE)된 팀의 구성원이 표시 정보를 전체 교체한다.
 */
class UpdateTeamE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 초대중(INVITING) 팀을 만들고 teamId를 돌려준다.
	fun invitingTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	// 결성(ACTIVE)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락)
	fun formedTeam(ownerId: Long, invitedUserId: Long): Long {
		val teamId: Long = invitingTeam(ownerId, invitedUserId)
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	describe("PUT /teams/v1/{teamId}") {

		context("INVITING 팀의 구성원이 수정하면") {
			it("이름·소개·활동지역이 바뀐 팀을 반환한다 (200)") {
				val ownerId = 5001L
				val invitedUserId = 5002L
				val teamId: Long = invitingTeam(ownerId, invitedUserId)

				put("/teams/v1/$teamId") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"regionId": 2, "name": "바꾼 팀 이름", "introduction": "새롭게 바꾼 팀 소개입니다"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.teamId", teamId.toInt())
					body("data.name", "바꾼 팀 이름")
					body("data.introduction", "새롭게 바꾼 팀 소개입니다")
					body("data.regionId", 2)
					body("data.status", "INVITING")
				}
			}
		}

		context("결성(ACTIVE)된 팀의 구성원이 수정하면") {
			it("초대받았던 구성원도 수정할 수 있다 (200)") {
				val ownerId = 5003L
				val invitedUserId = 5004L
				val teamId: Long = formedTeam(ownerId, invitedUserId)

				put("/teams/v1/$teamId") {
					bearer(accessTokenFor(invitedUserId))
					jsonBody("""{"regionId": 3, "name": "결성팀 새이름", "introduction": "결성 후 바꾼 팀 소개입니다"}""")
				} expect {
					status(200)
					body("data.name", "결성팀 새이름")
					body("data.regionId", 3)
					body("data.status", "ACTIVE")
				}
			}
		}

		context("팀 구성원이 아닌 사용자가 수정하면") {
			it("403(TEAM-006)을 반환한다") {
				val ownerId = 5005L
				val invitedUserId = 5006L
				val strangerId = 5007L
				val teamId: Long = invitingTeam(ownerId, invitedUserId)
				persistMatchUser(strangerId, Gender.MALE)

				put("/teams/v1/$teamId") {
					bearer(accessTokenFor(strangerId))
					jsonBody("""{"regionId": 2, "name": "바꾼 팀 이름", "introduction": "새롭게 바꾼 팀 소개입니다"}""")
				} expect {
					status(403)
					body("error.code", "TEAM-006")
				}
			}
		}

		context("소개가 10자 미만이면") {
			it("400을 반환한다 (요청 검증)") {
				val ownerId = 5008L
				val invitedUserId = 5009L
				val teamId: Long = invitingTeam(ownerId, invitedUserId)

				put("/teams/v1/$teamId") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"regionId": 2, "name": "바꾼 팀 이름", "introduction": "짧아요"}""")
				} expect {
					status(400)
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
