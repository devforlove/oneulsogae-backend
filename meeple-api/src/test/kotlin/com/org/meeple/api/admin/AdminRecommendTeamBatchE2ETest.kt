package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.greaterThanOrEqualTo

/**
 * `POST /admin/v1/teams/recommend-batch` E2E 테스트. (관리자 전용 팀 추천 일일 배치 수동 실행)
 * 팀 없는 솔로 유저에게 반대 성별·같은 권역의 ACTIVE 팀 1개를 추천 적재한다. ROLE_ADMIN만 접근 가능.
 */
class AdminRecommendTeamBatchE2ETest : AbstractIntegrationSupport({

	// ACTIVE 팀(여성 2명, 같은 권역)을 영속하고 teamId를 돌려준다. (팀원 match_user도 적재)
	fun persistActiveFemaleTeam(member1: Long, member2: Long, regionCode: Int): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "여성팀", gender = Gender.FEMALE, introduction = "즐겁게 만나요", status = TeamStatus.ACTIVE),
		)
		val teamId: Long = team.id!!
		listOf(member1, member2).forEach { userId: Long ->
			IntegrationUtil.persist(
				TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.ACTIVE),
			)
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.FEMALE, regionCode = regionCode))
		}
		return teamId
	}

	fun recommendationOf(userId: Long): RecommendedTeamEntity? {
		val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
		return IntegrationUtil.getQuery().selectFrom(recommended).where(recommended.userId.eq(userId)).fetchOne()
	}

	describe("POST /admin/v1/teams/recommend-batch") {

		context("팀 없는 솔로 유저와 반대 성별·같은 권역 ACTIVE 팀이 있으면") {
			it("그 유저에게 그 팀을 추천 적재한다 (200)") {
				val soloUserId = 4001L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				val teamId: Long = persistActiveFemaleTeam(member1 = 4101L, member2 = 4102L, regionCode = 1)

				post("/admin/v1/teams/recommend-batch") {
					bearer(adminAccessTokenFor(9101L))
				} expect {
					status(200)
					body("success", true)
					body("data.recommended", greaterThanOrEqualTo(1))
				}

				val recommendation: RecommendedTeamEntity? = recommendationOf(soloUserId)
				(recommendation != null) shouldBe true
				recommendation!!.teamId shouldBe teamId
			}
		}

		context("다시 실행하면") {
			it("유저당 추천 1행을 유지(교체)한다 (200)") {
				val soloUserId = 4002L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				persistActiveFemaleTeam(member1 = 4201L, member2 = 4202L, regionCode = 1)

				post("/admin/v1/teams/recommend-batch") { bearer(adminAccessTokenFor(9102L)) } expect { status(200) }
				post("/admin/v1/teams/recommend-batch") { bearer(adminAccessTokenFor(9102L)) } expect { status(200) }

				val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
				val count: Long = IntegrationUtil.getQuery().select(recommended.count()).from(recommended)
					.where(recommended.userId.eq(soloUserId)).fetchOne() ?: 0L
				count shouldBe 1L
			}
		}

		context("일반 사용자(ROLE_USER)가 호출하면") {
			it("403을 반환한다") {
				post("/admin/v1/teams/recommend-batch") { bearer(accessTokenFor(9103L)) } expect { status(403) }
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/admin/v1/teams/recommend-batch") {} expect { status(401) }
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
