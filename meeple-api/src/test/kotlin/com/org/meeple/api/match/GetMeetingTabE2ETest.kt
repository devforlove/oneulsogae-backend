package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RecommendedTeamEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDate

/**
 * `GET /teams/v1/meeting-tab` E2E 테스트. (미팅탭 화면 집계)
 * 추천 팀(recommendedTeam, 없으면 null) + 받은 초대 개수(receivedInvitationCount) + 내 결성 팀(myActiveTeam, 없으면 null)을 한 번에 반환한다.
 */
class GetMeetingTabE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender = Gender.MALE, regionCode: Int = 1, profileImageCode: String = "1") {
		IntegrationUtil.persist(
			MatchUserEntityFixture.create(userId = userId, gender = gender, regionCode = regionCode, profileImageCode = profileImageCode),
		)
	}

	fun persistTeam(status: TeamStatus, gender: Gender): Long =
		IntegrationUtil.persist(TeamEntity(name = "팀", gender = gender, introduction = "함께 즐겁게 활동해요", status = status)).id!!

	fun persistMember(teamId: Long, userId: Long, gender: Gender, status: TeamMemberStatus) {
		IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = userId, gender = gender, status = status))
	}

	describe("GET /teams/v1/meeting-tab") {

		context("추천 팀이 적재된 솔로 유저") {
			it("recommendedTeam에 팀·팀원을, count=0·myActiveTeam=null로 반환한다 (200)") {
				val soloUserId = 5001L
				persistMatchUser(soloUserId, Gender.MALE, 1)
				val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE)
				persistMember(teamId, 5101L, Gender.FEMALE, TeamMemberStatus.ACTIVE)
				persistMember(teamId, 5102L, Gender.FEMALE, TeamMemberStatus.ACTIVE)
				persistMatchUser(5101L, Gender.FEMALE, 1)
				persistMatchUser(5102L, Gender.FEMALE, 1)
				IntegrationUtil.persist(
					RecommendedTeamEntityFixture.create(userId = soloUserId, teamId = teamId, recommendedDate = LocalDate.of(2026, 6, 22)),
				)

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.recommendedTeam.teamId", teamId.toInt())
					body("data.recommendedTeam.members", hasSize<Any>(2))
					body("data.receivedInvitationCount", 0)
					body("data.myActiveTeam", nullValue())
				}
			}
		}

		context("초대를 2건 받은 유저") {
			it("receivedInvitationCount=2를 반환한다 (200)") {
				val me = 5002L
				persistMatchUser(me, Gender.MALE, 1)
				repeat(2) { i: Int ->
					val ownerId: Long = 5200L + i
					val teamId: Long = persistTeam(TeamStatus.INVITING, Gender.MALE)
					persistMember(teamId, ownerId, Gender.MALE, TeamMemberStatus.ACTIVE)
					persistMember(teamId, me, Gender.MALE, TeamMemberStatus.INVITED)
				}

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.receivedInvitationCount", 2)
					body("data.recommendedTeam", nullValue())
					body("data.myActiveTeam", nullValue())
				}
			}
		}

		context("결성(ACTIVE) 팀에 속한 유저") {
			it("myActiveTeam에 teamId와 내/친구 profileImageCode를 반환한다 (200)") {
				val me = 5003L
				val friend = 5301L
				persistMatchUser(me, Gender.MALE, 1, profileImageCode = "3")
				persistMatchUser(friend, Gender.MALE, 1, profileImageCode = "7")
				val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.MALE)
				persistMember(teamId, me, Gender.MALE, TeamMemberStatus.ACTIVE)
				persistMember(teamId, friend, Gender.MALE, TeamMemberStatus.ACTIVE)

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.myActiveTeam.teamId", teamId.toInt())
					body("data.myActiveTeam.myProfileImageCode", "3")
					body("data.myActiveTeam.partnerProfileImageCode", "7")
					body("data.recommendedTeam", nullValue())
					body("data.receivedInvitationCount", 0)
				}
			}
		}

		context("추천·초대·결성 팀이 모두 없는 유저") {
			it("recommendedTeam=null, count=0, myActiveTeam=null을 반환한다 (200)") {
				val me = 5004L
				persistMatchUser(me, Gender.MALE, 1)

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.recommendedTeam", nullValue())
					body("data.receivedInvitationCount", 0)
					body("data.myActiveTeam", nullValue())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})
