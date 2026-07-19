package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.CompanyEmailVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserCompanyEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.teammatch.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.meeple.infra.teammatch.command.entity.TeamEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QCompanyEmailVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserCompanyEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.org.meeple.scheduler.common.command.application.port.out.RegionProximityPort
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 회사 이메일 인증으로 온보딩이 완료될 때 가까운 추천 팀을 적재하는 E2E 테스트.
 * (예전엔 미팅탭 조회가 추천이 비었을 때 그 자리에서 적재했으나, CQS를 위해 인증 완료 시점으로 옮겼다 — 조회는 순수 읽기)
 * 인증이 완료되면 match_user가 적재·커밋된 뒤(AFTER_COMMIT), 유저와 가장 가까운 반대 성별 ACTIVE 팀을 추천(recommended_teams)으로 적재한다.
 * 지역 매칭 스냅샷(근접·유저 분포·팀 분포)은 기동 시 1회 적재되므로, 테스트에서 지역·팀을 넣은 뒤 [RegionProximityPort.refresh]로 함께 갱신한다.
 */
class MeetingTabNearestRecommendationE2ETest : AbstractIntegrationSupport() {

	@Autowired
	private lateinit var regionProximityPort: RegionProximityPort

	init {
		// 온보딩 중(ONBOARDING) + 매칭 필수 필드를 갖춘 완성 프로필 + 회사 도메인 매핑 + 대기 중 인증번호를 갖춘 요청자를 만들고 생성된 userId를 반환한다.
		// 인증이 완료되면 ACTIVE로 전환돼 match_user가 적재되고(BEFORE_COMMIT), 커밋 직후 가까운 팀 추천이 적재된다(AFTER_COMMIT).
		fun persistVerifiableSoloUser(providerId: String, regionId: Long): Long {
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
			IntegrationUtil.persist(UserCompanyEntityFixture.create(emailDomain = "meeple.com", companyName = "미플"))
			return userId
		}

		fun persistTeam(status: TeamStatus, gender: Gender, regionId: Long): Long =
			IntegrationUtil.persist(
				TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "함께 즐겁게 활동해요", status = status),
			).id!!

		// 활성 팀원(프로필 포함). 멤버 조회가 match_user ⋈ user_details inner join이라 user_details도 필요하다.
		fun persistActiveMemberWithProfile(teamId: Long, userId: Long, gender: Gender, regionId: Long) {
			IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.ACTIVE))
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = gender, regionId = regionId))
		}

		// 온보딩 완료를 요청해 정식 가입(ACTIVE) 처리한다. 가입 완료 시점에 match_user 적재·첫 소개·가까운 팀 추천이 처리된다.
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

		fun recommendedTeamIdOf(userId: Long): Long? {
			val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
			return IntegrationUtil.getQuery()
				.select(recommended.teamId)
				.from(recommended)
				.where(recommended.userId.eq(userId))
				.fetchFirst()
		}

		describe("회사 이메일 인증으로 온보딩이 완료되면") {

			context("가까운 반대 성별 ACTIVE 팀이 있으면") {
				it("가장 가까운 팀을 추천으로 적재하고, 미팅탭 조회에 카드로 내려준다 (200)") {
					val nearRegionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구", latitude = 37.5172, longitude = 127.0473),
					).id!!
					// 더 먼 권역. 가까운 권역(강남)의 팀이 우선 선택돼야 한다.
					val farRegionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "부산광역시", sigungu = "해운대구", latitude = 35.1631, longitude = 129.1635),
					).id!!

					val me: Long = persistVerifiableSoloUser(providerId = "verify-me", regionId = nearRegionId)

					// 가까운 권역의 반대 성별(FEMALE) ACTIVE 팀 = 선택 대상.
					val nearTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, nearRegionId)
					persistActiveMemberWithProfile(nearTeamId, 7101L, Gender.FEMALE, nearRegionId)
					persistActiveMemberWithProfile(nearTeamId, 7102L, Gender.FEMALE, nearRegionId)

					// 먼 권역의 반대 성별 ACTIVE 팀 = 더 가까운 팀이 있으니 선택되지 않아야 한다.
					val farTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, farRegionId)
					persistActiveMemberWithProfile(farTeamId, 7201L, Gender.FEMALE, farRegionId)
					persistActiveMemberWithProfile(farTeamId, 7202L, Gender.FEMALE, farRegionId)

					// 후보 팀 적재 후 지역 매칭 스냅샷(근접·팀 분포)을 갱신한다. (팀 분포 스냅샷이 강남에 FEMALE 팀이 있음을 알아야 그 지역을 건너뛰지 않는다)
					regionProximityPort.refresh()

					completeOnboarding(me, nearRegionId)

					// 가입 완료 직후 가장 가까운 팀이 recommended_teams에 적재됐는지 확인한다.
					recommendedTeamIdOf(me) shouldBe nearTeamId

					// 적재된 추천이 미팅탭 조회에 카드로 내려온다. (GET 자체는 부수효과 없음)
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
				}
			}

			context("반대 성별 후보 팀이 전혀 없으면") {
				it("추천을 적재하지 않고, 미팅탭은 빈 리스트를 반환한다 (200)") {
					val regionId: Long = IntegrationUtil.persist(
						RegionEntityFixture.create(sido = "서울특별시", sigungu = "서초구"),
					).id!!
					val me: Long = persistVerifiableSoloUser(providerId = "verify-me-2", regionId = regionId)

					regionProximityPort.refresh()

					completeOnboarding(me, regionId)

					recommendedTeamIdOf(me).shouldBeNull()

					get("/team-matches/v1/meeting-tab") {
						bearer(accessTokenFor(me))
					} expect {
						status(200)
						body("data.recommendedTeams", hasSize<Any>(0))
					}
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
			IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
			IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
			IntegrationUtil.deleteAll(QUserEntity.userEntity)
			IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
			// 다른 테스트에 스냅샷이 새지 않도록 비운 상태로 되돌린다.
			regionProximityPort.refresh()
		}
	}
}
