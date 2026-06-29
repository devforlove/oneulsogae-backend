package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.teammatch.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.meeple.infra.teammatch.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.TeamEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.scheduler.teammatch.command.application.port.`in`.RunRecommendedTeamBatchUseCase
import com.org.meeple.scheduler.teammatch.command.domain.RecommendedTeamBatchResult
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * [RunRecommendedTeamBatchUseCase](RecommendedTeamBatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 배치가 시작 시 regionProximityPort.refresh()로 근접 스냅샷을 적재하므로 regions·match_user·teams를 적재한 뒤 호출한다.
 * RegionShuffler는 TestRegionShufflerConfig가 항등(순서 유지)으로 고정 → 근접 우선이 결정적.
 */
class RunRecommendedTeamBatchIntegrationTest(
    private val runRecommendedTeamBatchUseCase: RunRecommendedTeamBatchUseCase,
) : AbstractIntegrationSupport({

    describe("run") {

        context("팀 없는 솔로 유저와 반대 성별·가까운 권역 ACTIVE 팀이 있으면") {
            it("그 유저에게 그 팀을 추천 적재한다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                val teamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 1
                result.failed shouldBe 0
                recommendationOf(soloUserId).shouldNotBeNull().teamId shouldBe teamId
            }
        }

        context("반대 성별 후보 팀이 없으면") {
            it("아무도 추천하지 못한다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                persistActiveTeam(gender = Gender.MALE, regionId = regionId) // 같은 성별 → 후보 아님

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 0
                recommendationOf(soloUserId).shouldBeNull()
            }
        }

        context("가까운 권역과 먼 권역에 후보 팀이 있으면") {
            it("가까운 권역의 팀을 추천한다") {
                val nearRegionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val farRegionId: Long = persistRegion("부산광역시", "해운대구", 35.16, 129.16)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = nearRegionId)
                val nearTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = nearRegionId)
                val farTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = farRegionId)

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 1
                val recommendedTeamId: Long = recommendationOf(soloUserId).shouldNotBeNull().teamId
                recommendedTeamId shouldBe nearTeamId
                (recommendedTeamId == farTeamId) shouldBe false
            }
        }

        context("오늘 이미 추천받은 유저는") {
            it("재실행해도 제외되어 기존 추천이 유지된다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val soloUserId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)
                // 오늘 일자로 다른 팀(9999)이 이미 추천돼 있다.
                IntegrationUtil.persist(RecommendedTeamEntity(userId = soloUserId, teamId = 9999L, recommendedDate = LocalDate.now()))

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 0
                recommendationOf(soloUserId).shouldNotBeNull().teamId shouldBe 9999L // 덮어쓰지 않음
            }
        }

        context("이미 팀에 속한 유저는") {
            it("추천 대상에서 제외된다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val teamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)
                // 5001은 male이지만 어떤 팀(teamId)의 멤버 → 팀 미소속 아님 → 대상 아님
                val memberId: Long = persistSoloUser(userId = 5001L, gender = Gender.MALE, regionId = regionId)
                IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = memberId, status = TeamMemberStatus.ACTIVE))
                // 추천 가능한 반대 성별 팀도 둔다 (대상이었다면 추천됐을 것)
                persistActiveTeam(gender = Gender.FEMALE, regionId = regionId)

                val result: RecommendedTeamBatchResult = runRecommendedTeamBatchUseCase.run()

                result.recommended shouldBe 0
                recommendationOf(memberId).shouldBeNull()
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

private fun persistRegion(sido: String, sigungu: String, latitude: Double, longitude: Double): Long {
    val region: RegionEntity = IntegrationUtil.persist(
        RegionEntityFixture.create(sido = sido, sigungu = sigungu, latitude = latitude, longitude = longitude),
    )
    return region.id!!
}

private fun persistSoloUser(userId: Long, gender: Gender, regionId: Long): Long {
    IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
    return userId
}

private fun persistActiveTeam(gender: Gender, regionId: Long): Long {
    val team: TeamEntity = IntegrationUtil.persist(
        TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "소개", status = TeamStatus.ACTIVE),
    )
    return team.id!!
}

private fun recommendationOf(userId: Long): RecommendedTeamEntity? {
    val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
    return IntegrationUtil.getQuery().selectFrom(recommended).where(recommended.userId.eq(userId)).fetchOne()
}
