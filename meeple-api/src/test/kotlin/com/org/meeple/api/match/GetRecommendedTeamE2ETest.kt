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
 * `GET /teams/v1/recommended-team` E2E 테스트. (팀 없는 솔로 유저의 미팅탭 추천 팀 표시)
 * 추천 행(recommended_teams)이 가리키는 ACTIVE 팀을 팀원 프로필과 함께 반환한다. 추천이 없거나 팀이 해체됐으면 data=null.
 */
class GetRecommendedTeamE2ETest : AbstractIntegrationSupport({

	// ACTIVE 팀 1개(여성 2명, 같은 권역)를 영속하고 teamId를 돌려준다. 팀원 match_user도 함께 적재한다.
	fun persistActiveFemaleTeam(member1: Long, member2: Long, regionCode: Int): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "여성팀", introduction = "즐겁게 만나요", status = TeamStatus.ACTIVE),
		)
		val teamId: Long = team.id!!
		listOf(member1, member2).forEach { userId: Long ->
			IntegrationUtil.persist(
				TeamMemberEntity(teamId = teamId, userId = userId, gender = Gender.FEMALE, status = TeamMemberStatus.ACTIVE),
			)
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.FEMALE, regionCode = regionCode))
		}
		return teamId
	}

	describe("GET /teams/v1/recommended-team") {

		context("추천 행이 ACTIVE 팀을 가리키면") {
			it("그 팀과 팀원 프로필을 반환한다 (200)") {
				val soloUserId = 3001L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				val teamId: Long = persistActiveFemaleTeam(member1 = 3101L, member2 = 3102L, regionCode = 1)
				IntegrationUtil.persist(
					RecommendedTeamEntityFixture.create(userId = soloUserId, teamId = teamId, recommendedDate = LocalDate.of(2026, 6, 22)),
				)

				get("/teams/v1/recommended-team") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.teamId", teamId.toInt())
					body("data.name", "여성팀")
					body("data.members", hasSize<Any>(2))
				}
			}
		}

		context("추천 행이 없으면") {
			it("data=null을 반환한다 (200)") {
				val soloUserId = 3002L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))

				get("/teams/v1/recommended-team") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("data", nullValue())
				}
			}
		}

		context("추천 행이 가리키는 팀이 해체(soft delete)됐으면") {
			it("data=null을 반환한다 (200)") {
				val soloUserId = 3003L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				val team: TeamEntity = IntegrationUtil.persist(
					TeamEntity(name = "해체팀", introduction = "사라질 팀", status = TeamStatus.DEACTIVATED).also { it.softDelete(java.time.LocalDateTime.of(2026, 6, 21, 0, 0)) },
				)
				IntegrationUtil.persist(
					RecommendedTeamEntityFixture.create(userId = soloUserId, teamId = team.id!!, recommendedDate = LocalDate.of(2026, 6, 22)),
				)

				get("/teams/v1/recommended-team") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("data", nullValue())
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
