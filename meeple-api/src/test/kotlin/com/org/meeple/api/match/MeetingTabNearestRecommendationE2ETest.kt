package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.region.RegionProximityRegistry
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.springframework.beans.factory.annotation.Autowired

/**
 * `GET /team-matches/v1/meeting-tab` 가까운 추천 생성 E2E 테스트.
 * 추천(recommended_teams)이 없는 솔로 유저면, 유저와 가장 가까운 반대 성별 ACTIVE 팀을 새 추천으로 적재해 카드로 내려준다.
 * 근접 스냅샷([RegionProximityRegistry])은 기동 시 1회 적재되므로, 테스트에서 지역을 넣은 뒤 refresh로 갱신한다.
 */
class MeetingTabNearestRecommendationE2ETest : AbstractIntegrationSupport() {

	@Autowired
	private lateinit var regionProximityRegistry: RegionProximityRegistry

	init {
		fun persistMatchUser(userId: Long, gender: Gender, regionId: Long) {
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
		}

		fun persistTeam(status: TeamStatus, gender: Gender, regionId: Long): Long =
			IntegrationUtil.persist(
				TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "함께 즐겁게 활동해요", status = status),
			).id!!

		// 활성 팀원(프로필 포함). 멤버 조회가 match_user ⋈ user_details inner join이라 user_details도 필요하다.
		fun persistActiveMemberWithProfile(teamId: Long, userId: Long, gender: Gender, regionId: Long) {
			IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.ACTIVE))
			persistMatchUser(userId, gender, regionId)
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
		}

		fun recommendedTeamIdOf(userId: Long): Long? {
			val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
			return IntegrationUtil.getQuery()
				.select(recommended.teamId)
				.from(recommended)
				.where(recommended.userId.eq(userId))
				.fetchFirst()
		}

		describe("GET /team-matches/v1/meeting-tab - 가까운 추천 생성") {

			context("추천이 없는 솔로 유저이고 가까운 반대 성별 ACTIVE 팀이 있으면") {
				it("가장 가까운 팀을 새 추천으로 적재하고 카드로 내려준다 (200)") {
					val me = 7001L
					val nearRegionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.5172, longitude = 127.0473),
					).id!!
					// 더 먼 권역. 가까운 권역(강남)의 팀이 우선 선택돼야 한다.
					val farRegionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "부산광역시", sigungu = "해운대구", latitude = 35.1631, longitude = 129.1635),
					).id!!

					persistMatchUser(me, Gender.MALE, nearRegionId)

					// 가까운 권역의 반대 성별(FEMALE) ACTIVE 팀 = 선택 대상.
					val nearTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, nearRegionId)
					persistActiveMemberWithProfile(nearTeamId, 7101L, Gender.FEMALE, nearRegionId)
					persistActiveMemberWithProfile(nearTeamId, 7102L, Gender.FEMALE, nearRegionId)

					// 먼 권역의 반대 성별 ACTIVE 팀 = 더 가까운 팀이 있으니 선택되지 않아야 한다.
					val farTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, farRegionId)
					persistActiveMemberWithProfile(farTeamId, 7201L, Gender.FEMALE, farRegionId)
					persistActiveMemberWithProfile(farTeamId, 7202L, Gender.FEMALE, farRegionId)

					regionProximityRegistry.refresh()

					get("/team-matches/v1/meeting-tab") {
						bearer(accessTokenFor(me))
					} expect {
						status(200)
						body("data.myTeam", nullValue())
						body("data.recommendedTeams", hasSize<Any>(1))
						body("data.recommendedTeams[0].teamId", nearTeamId.toInt())
						body("data.recommendedTeams[0].members", hasSize<Any>(2))
						// 순수 추천이라 아직 매칭이 없다.
						body("data.recommendedTeams[0].teamMatchId", nullValue())
					}

					// 가장 가까운 팀이 recommended_teams에 적재됐는지 확인한다.
					recommendedTeamIdOf(me) shouldBe nearTeamId
				}
			}

			context("추천이 없고 반대 성별 후보 팀도 전혀 없으면") {
				it("빈 리스트를 반환하고 추천도 적재하지 않는다 (200)") {
					val me = 7002L
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "서초구"),
					).id!!
					persistMatchUser(me, Gender.MALE, regionId)

					regionProximityRegistry.refresh()

					get("/team-matches/v1/meeting-tab") {
						bearer(accessTokenFor(me))
					} expect {
						status(200)
						body("data.recommendedTeams", hasSize<Any>(0))
					}

					recommendedTeamIdOf(me).shouldBeNull()
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
			IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
			IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
			IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
			IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
			IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
			// 다른 테스트에 스냅샷이 새지 않도록 비운 상태로 되돌린다.
			regionProximityRegistry.refresh()
		}
	}
}
