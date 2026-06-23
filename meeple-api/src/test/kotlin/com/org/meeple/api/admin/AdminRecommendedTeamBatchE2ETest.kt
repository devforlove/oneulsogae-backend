package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.greaterThanOrEqualTo

/**
 * `POST /admin/v1/teams/recommend-batch` E2E 테스트. (관리자 전용 근접 팀 추천 일일 배치 수동 실행)
 * 팀 없는 솔로 유저에게 가까운 권역의 반대 성별 ACTIVE 팀 1개를 추천 적재한다. ROLE_ADMIN만 접근 가능.
 */
class AdminRecommendedTeamBatchE2ETest : AbstractIntegrationSupport({

    fun persistRegion(): Long {
        val region: RegionEntity = IntegrationUtil.persist(
            RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.50, longitude = 127.00),
        )
        return region.id!!
    }

    fun persistActiveFemaleTeam(regionId: Long): Long {
        val team: TeamEntity = IntegrationUtil.persist(
            TeamEntity(name = "여성팀", gender = Gender.FEMALE, regionId = regionId, introduction = "즐겁게 만나요", status = TeamStatus.ACTIVE),
        )
        return team.id!!
    }

    fun recommendationOf(userId: Long): RecommendedTeamEntity? {
        val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
        return IntegrationUtil.getQuery().selectFrom(recommended).where(recommended.userId.eq(userId)).fetchOne()
    }

    describe("POST /admin/v1/teams/recommend-batch") {

        context("팀 없는 솔로 유저와 반대 성별·가까운 권역 ACTIVE 팀이 있으면") {
            it("그 유저에게 그 팀을 추천 적재한다 (200)") {
                val regionId: Long = persistRegion()
                val soloUserId = 4001L
                IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionId = regionId))
                val teamId: Long = persistActiveFemaleTeam(regionId)

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

        context("오늘 이미 추천된 유저는") {
            it("재실행해도 추천 1행을 유지한다 (200)") {
                val regionId: Long = persistRegion()
                val soloUserId = 4002L
                IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionId = regionId))
                persistActiveFemaleTeam(regionId)

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
        IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
    }
})
