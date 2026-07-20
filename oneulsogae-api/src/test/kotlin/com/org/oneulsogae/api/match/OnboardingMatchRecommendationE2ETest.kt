package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.SoloMatchType
import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QRecommendedTeamEntity
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMemberEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.scheduler.common.command.application.port.out.RegionProximityPort
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * 온보딩 완료(POST /users/v1/onboarding/complete)로 정식 가입될 때 1:1 매칭 소개([RecommendMatch])와
 * 팀 추천([RecommendTeam])이 함께 동작하는지 검증하는 E2E 테스트.
 *
 * 가입이 완료되면 같은 트랜잭션에서 매칭 읽기 모델(match_user)이 동기 적재된 뒤,
 * 가까운 반대 성별 후보로 1:1 소개(solo_matches, PROPOSED·ONBOARDING)를 적재한다.
 * 가입 완료 시점에 가까운 반대 성별 ACTIVE 팀이 있으면 팀 추천(recommended_teams)도 함께 적재된다.
 * 지역 매칭 스냅샷(근접·유저/팀 분포)은 기동 시 1회 적재되므로, 테스트에서 지역·후보·팀을 넣은 뒤 [RegionProximityPort.refresh]로 함께 갱신한다.
 */
class OnboardingMatchRecommendationE2ETest : AbstractIntegrationSupport() {

	@Autowired
	private lateinit var regionProximityPort: RegionProximityPort

	init {
		// 온보딩 중(ONBOARDING)인 남성 요청자를 만들고 userId를 반환한다. 프로필은 온보딩 완료 요청 본문으로 입력된다.
		fun persistOnboardingMaleUser(providerId: String): Long =
			IntegrationUtil.persist(
				UserEntityFixture.create(providerId = providerId, status = UserStatus.ONBOARDING, lastLoginAt = LocalDateTime.now()),
			).id!!

		// 반대 성별(여성) 1:1 후보를 매칭 읽기 모델에만 적재한다. (1:1 소개는 프로필 조인 없이 match_user만으로 후보를 고른다)
		fun persistFemaleCandidate(userId: Long, regionId: Long): Long {
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.FEMALE, regionId = regionId))
			return userId
		}

		fun persistTeam(status: TeamStatus, gender: Gender, regionId: Long): Long =
			IntegrationUtil.persist(
				TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "함께 즐겁게 활동해요", status = status),
			).id!!

		// 활성 팀원(프로필 포함). 팀원은 match_user에도 적재되므로 1:1 소개 후보로도 잡힌다.
		fun persistActiveMemberWithProfile(teamId: Long, userId: Long, gender: Gender, regionId: Long) {
			IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.ACTIVE))
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
		}

		// 온보딩 완료를 요청해 정식 가입(ACTIVE) 처리한다. 가입 완료 시점에 match_user 적재·첫 소개·팀 추천이 처리된다.
		fun completeOnboarding(userId: Long, regionId: Long) {
			post("/users/v1/onboarding/complete") {
				bearer(accessTokenFor(userId))
				jsonBody(
					"""
					{
					  "nickname": "철수",
					  "birthday": "1996-01-01",
					  "height": 175,
					  "gender": "MALE",
					  "phoneNumber": "010-1234-5678",
					  "job": "개발자",
					  "regionId": $regionId,
					  "introduction": "안녕하세요 잘 부탁드립니다.",
					  "traits": ["성실함"],
					  "interests": ["영화"],
					  "maritalStatus": "SINGLE",
					  "smokingStatus": "NON_SMOKER",
					  "religion": "NONE",
					  "drinkingStatus": "SOMETIMES",
					  "bodyType": "MALE_NORMAL"
					}
					""".trimIndent(),
				)
			} expect {
				status(200)
				body("success", true)
			}
		}

		// 요청자가 참가한 PROPOSED·ONBOARDING 1:1 소개의 상대(파트너) userId. 소개가 없으면 null.
		fun proposedOnboardingPartnerOf(requesterId: Long): Long? {
			val me: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
			val other: QSoloMatchMemberEntity = QSoloMatchMemberEntity("other")
			val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
			return IntegrationUtil.getQuery()
				.select(other.userId)
				.from(me)
				.join(match).on(match.id.eq(me.matchId))
				.join(other).on(other.matchId.eq(me.matchId))
				.where(
					me.userId.eq(requesterId),
					other.userId.ne(requesterId),
					match.status.eq(MatchStatus.PROPOSED),
					match.matchType.eq(SoloMatchType.ONBOARDING),
				)
				.fetchFirst()
		}

		fun recommendedTeamIdOf(userId: Long): Long? {
			val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
			return IntegrationUtil.getQuery()
				.select(recommended.teamId)
				.from(recommended)
				.where(recommended.userId.eq(userId))
				.fetchFirst()
		}

		describe("온보딩 완료(complete)로 정식 가입되면") {

			context("가까운 반대 성별 1:1 후보가 있으면") {
				it("그 후보를 1:1 매칭(PROPOSED·ONBOARDING)으로 소개한다") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.5172, longitude = 127.0473),
					).id!!
					val me: Long = persistOnboardingMaleUser(providerId = "complete-solo-me")
					val candidate: Long = persistFemaleCandidate(userId = 8101L, regionId = regionId)

					// 후보 match_user 적재 후 유저 분포 스냅샷을 갱신한다. (강남에 여성 후보가 있음을 알아야 그 지역을 건너뛰지 않는다)
					regionProximityPort.refresh()

					completeOnboarding(me, regionId)

					proposedOnboardingPartnerOf(me) shouldBe candidate
				}
			}

			context("반대 성별 1:1 후보가 전혀 없으면") {
				it("1:1 소개 없이 가입만 완료된다") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "서초구"),
					).id!!
					val me: Long = persistOnboardingMaleUser(providerId = "complete-solo-empty")

					regionProximityPort.refresh()

					completeOnboarding(me, regionId)

					proposedOnboardingPartnerOf(me).shouldBeNull()
				}
			}

			context("가까운 반대 성별 ACTIVE 팀(=여성 팀원)이 있으면") {
				it("1:1 소개와 가까운 팀 추천이 함께 적재된다") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.5172, longitude = 127.0473),
					).id!!
					val me: Long = persistOnboardingMaleUser(providerId = "complete-both-me")

					// 반대 성별(FEMALE) ACTIVE 팀. 팀원들은 match_user에도 적재되므로 1:1 소개 후보이기도 하다.
					val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, regionId)
					persistActiveMemberWithProfile(teamId, 8201L, Gender.FEMALE, regionId)
					persistActiveMemberWithProfile(teamId, 8202L, Gender.FEMALE, regionId)

					// 후보·팀 적재 후 지역 매칭 스냅샷(유저/팀 분포)을 갱신한다.
					regionProximityPort.refresh()

					completeOnboarding(me, regionId)

					// 가입 완료 직후 가까운 반대 성별 ACTIVE 팀이 추천으로 적재된다.
					recommendedTeamIdOf(me) shouldBe teamId
					// 1:1 소개도 함께 적재됐다. (상대는 팀의 여성 팀원 중 하나 — 로그인 시각 동점이라 둘 중 하나)
					proposedOnboardingPartnerOf(me) shouldBeIn listOf(8201L, 8202L)
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
			IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
			IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
			IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
			IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
			IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
			IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
			IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
			IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
			IntegrationUtil.deleteAll(QUserEntity.userEntity)
			IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
			// 다른 테스트에 스냅샷이 새지 않도록 비운 상태로 되돌린다.
			regionProximityPort.refresh()
		}
	}
}
