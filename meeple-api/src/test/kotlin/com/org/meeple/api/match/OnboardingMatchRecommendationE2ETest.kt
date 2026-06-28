package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.coin.command.entity.QCoinBalanceEntity
import com.org.meeple.infra.coin.command.entity.QCoinHistoryEntity
import com.org.meeple.infra.fixture.CompanyEmailVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserCompanyEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QCompanyEmailVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserCompanyEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 회사 이메일 인증으로 온보딩이 완료될 때 1:1 매칭 소개([RecommendMatch])와 팀 추천([RecommendTeam])이 함께 동작하는지 검증하는 E2E 테스트.
 *
 * 인증이 완료되면 같은 트랜잭션에서 매칭 읽기 모델(match_user)이 동기 적재된 뒤,
 * 가까운 반대 성별 후보로 1:1 소개(solo_matches, PROPOSED·ONBOARDING)를, 가까운 반대 성별 ACTIVE 팀으로 추천(recommended_teams)을 적재한다.
 * 지역 매칭 스냅샷(근접·유저/팀 분포)은 기동 시 1회 적재되므로, 테스트에서 지역·후보·팀을 넣은 뒤 [RegionProximityPort.refresh]로 함께 갱신한다.
 */
class OnboardingMatchRecommendationE2ETest : AbstractIntegrationSupport() {

	@Autowired
	private lateinit var regionProximityPort: RegionProximityPort

	init {
		// 온보딩 중(ONBOARDING) + 매칭 필수 필드를 갖춘 완성 프로필 + 회사 도메인 매핑 + 대기 중 인증번호를 갖춘 남성 요청자를 만들고 userId를 반환한다.
		// 인증이 완료되면 ACTIVE로 전환돼 match_user가 동기 적재되고, 곧바로 1:1 소개·팀 추천이 같은 트랜잭션에서 처리된다.
		// withCompanyMapping=false면 회사 도메인 매핑을 넣지 않아, 인증은 통과하되 회사명을 못 찾아 COMPANY_NOT_RESOLVED로 확정된다.
		fun persistVerifiableMaleUser(providerId: String, regionId: Long, withCompanyMapping: Boolean = true): Long {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = providerId, status = UserStatus.ONBOARDING, lastLoginAt = LocalDateTime.now()),
			).id!!
			IntegrationUtil.persist(
				UserDetailEntity(
					userId = userId,
					nickname = "철수",
					profileImageCode = "1",
					gender = Gender.MALE,
					birthday = LocalDate.of(1996, 1, 1),
					regionId = regionId,
					maritalStatus = MaritalStatus.SINGLE,
				),
			)
			IntegrationUtil.persist(
				CompanyEmailVerificationEntityFixture.create(userId = userId, companyEmail = "me@meeple.com", code = "123456"),
			)
			if (withCompanyMapping) {
				IntegrationUtil.persist(UserCompanyEntityFixture.create(emailDomain = "meeple.com", companyName = "미플"))
			}
			return userId
		}

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

		fun verifyCompanyEmail(userId: Long, companyResolved: Boolean = true) {
			post("/users/v1/onboarding/company-email/verifications/confirm") {
				bearer(accessTokenFor(userId))
				jsonBody("""{"code": "123456"}""")
			} expect {
				status(200)
				body("success", true)
				body("data.justOnboarded", true)
				body("data.isCompanyResolved", companyResolved)
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

		describe("회사 이메일 인증으로 온보딩이 완료되면") {

			context("가까운 반대 성별 1:1 후보가 있으면") {
				it("그 후보를 1:1 매칭(PROPOSED·ONBOARDING)으로 소개한다") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.5172, longitude = 127.0473),
					).id!!
					val me: Long = persistVerifiableMaleUser(providerId = "verify-solo-me", regionId = regionId)
					val candidate: Long = persistFemaleCandidate(userId = 8101L, regionId = regionId)

					// 후보 match_user 적재 후 유저 분포 스냅샷을 갱신한다. (강남에 여성 후보가 있음을 알아야 그 지역을 건너뛰지 않는다)
					regionProximityPort.refresh()

					verifyCompanyEmail(me)

					proposedOnboardingPartnerOf(me) shouldBe candidate
				}
			}

			context("반대 성별 1:1 후보가 전혀 없으면") {
				it("1:1 소개 없이 가입만 완료된다") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "서초구"),
					).id!!
					val me: Long = persistVerifiableMaleUser(providerId = "verify-solo-empty", regionId = regionId)

					regionProximityPort.refresh()

					verifyCompanyEmail(me)

					proposedOnboardingPartnerOf(me).shouldBeNull()
				}
			}

			context("가까운 반대 성별 ACTIVE 팀(=여성 팀원)이 있으면") {
				it("1:1 소개와 팀 추천을 함께 적재한다") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.5172, longitude = 127.0473),
					).id!!
					val me: Long = persistVerifiableMaleUser(providerId = "verify-both-me", regionId = regionId)

					// 반대 성별(FEMALE) ACTIVE 팀. 팀원들은 match_user에도 적재되므로 1:1 소개 후보이기도 하다.
					val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, regionId)
					persistActiveMemberWithProfile(teamId, 8201L, Gender.FEMALE, regionId)
					persistActiveMemberWithProfile(teamId, 8202L, Gender.FEMALE, regionId)

					// 후보·팀 적재 후 지역 매칭 스냅샷(유저/팀 분포)을 갱신한다.
					regionProximityPort.refresh()

					verifyCompanyEmail(me)

					// 팀 추천이 적재됐다.
					recommendedTeamIdOf(me) shouldBe teamId
					// 같은 인증으로 1:1 소개도 적재됐다. (상대는 팀의 여성 팀원 중 하나 — 로그인 시각 동점이라 둘 중 하나)
					proposedOnboardingPartnerOf(me) shouldBeIn listOf(8201L, 8202L)
				}
			}

			context("회사 도메인 매핑이 없어 COMPANY_NOT_RESOLVED로 확정돼도") {
				it("회사 이메일 인증을 마친 사용자이므로 1:1 소개와 팀 추천이 ACTIVE와 똑같이 적재된다") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.5172, longitude = 127.0473),
					).id!!
					// 회사 매핑이 없어 인증은 통과하되 COMPANY_NOT_RESOLVED로 확정된다. (isMatchable=true → 매칭 대상)
					val me: Long = persistVerifiableMaleUser(providerId = "verify-unresolved-me", regionId = regionId, withCompanyMapping = false)

					// 가까운 반대 성별 ACTIVE 팀. 팀원들은 match_user에도 적재돼 1:1 소개 후보이기도 하다.
					val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, regionId)
					persistActiveMemberWithProfile(teamId, 8302L, Gender.FEMALE, regionId)
					persistActiveMemberWithProfile(teamId, 8303L, Gender.FEMALE, regionId)

					regionProximityPort.refresh()

					verifyCompanyEmail(me, companyResolved = false)

					// COMPANY_NOT_RESOLVED여도 match_user가 적재돼 팀 추천과 1:1 소개가 함께 적재된다.
					recommendedTeamIdOf(me) shouldBe teamId
					proposedOnboardingPartnerOf(me) shouldBeIn listOf(8302L, 8303L)
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
			IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
			IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
			IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
			IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
			IntegrationUtil.deleteAll(QCompanyEmailVerificationEntity.companyEmailVerificationEntity)
			IntegrationUtil.deleteAll(QUserCompanyEntity.userCompanyEntity)
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
