package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RecommendedTeamEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `GET /teams/v1/meeting-tab` E2E 테스트. (미팅탭 화면 집계)
 * 추천 팀 목록(recommendedTeams, 없으면 빈 리스트) + 받은 초대 개수(receivedInvitationCount) + 내 결성 팀(myActiveTeam, 없으면 null)을 한 번에 반환한다.
 */
class GetMeetingTabE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender = Gender.MALE, profileImageCode: String = "1") {
		IntegrationUtil.persist(
			MatchUserEntityFixture.create(userId = userId, gender = gender, profileImageCode = profileImageCode),
		)
	}

	fun persistTeam(status: TeamStatus, gender: Gender, regionId: Long = 1L): Long =
		IntegrationUtil.persist(
			TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "함께 즐겁게 활동해요", status = status),
		).id!!

	fun persistMember(teamId: Long, userId: Long, status: TeamMemberStatus) {
		IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = userId, status = status))
	}

	fun persistTeamMatch(memberKey: String, expiresAt: LocalDateTime, status: MatchStatus = MatchStatus.PROPOSED): Long =
		IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = memberKey,
				introducedDate = LocalDate.of(2026, 6, 24),
				expiresAt = expiresAt,
				status = status,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = 40,
				dateAcceptAmount = 40,
			),
		).id!!

	fun persistMatchedTeam(teamMatchId: Long, teamId: Long) {
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamId))
	}

	describe("GET /teams/v1/meeting-tab") {

		context("추천 팀이 적재된 솔로 유저") {
			it("recommendedTeams에 팀·팀원 프로필을, count=0·myActiveTeam=null로 반환한다 (200)") {
				val soloUserId = 5001L
				persistMatchUser(soloUserId, Gender.MALE)
				val gangnamId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!
				val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, regionId = gangnamId)
				persistMember(teamId, 5101L, TeamMemberStatus.ACTIVE)
				persistMember(teamId, 5102L, TeamMemberStatus.ACTIVE)
				persistMatchUser(5101L, Gender.FEMALE)
				persistMatchUser(5102L, Gender.FEMALE)
				// 팀원 상세 프로필(user_details). 멤버 조회가 match_user ⋈ user_details inner join이라 필수.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = 5101L, gender = Gender.FEMALE, job = "디자이너", companyName = "카카오",
						height = 165, regionId = gangnamId, introduction = "반가워요",
					),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = 5102L, gender = Gender.FEMALE),
				)
				IntegrationUtil.persist(
					RecommendedTeamEntityFixture.create(userId = soloUserId, teamId = teamId, recommendedDate = LocalDate.of(2026, 6, 22)),
				)

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.recommendedTeams", hasSize<Any>(1))
					body("data.recommendedTeams[0].teamId", teamId.toInt())
					body("data.recommendedTeams[0].activityArea", "서울특별시 강남구")
					body("data.recommendedTeams[0].members", hasSize<Any>(2))
					// 멤버는 userId asc → [0]=5101. user_details 상세(직업·회사명·키·지역·자기소개)와 특성·관심사(빈 배열)를 담는다.
					body("data.recommendedTeams[0].members[0].userId", 5101)
					body("data.recommendedTeams[0].members[0].job", "디자이너")
					body("data.recommendedTeams[0].members[0].companyName", "카카오")
					body("data.recommendedTeams[0].members[0].height", 165)
					body("data.recommendedTeams[0].members[0].activityArea", "서울특별시 강남구")
					body("data.recommendedTeams[0].members[0].introduction", "반가워요")
					body("data.recommendedTeams[0].members[0].traits", hasSize<Any>(0))
					body("data.recommendedTeams[0].members[0].interests", hasSize<Any>(0))
					body("data.receivedInvitationCount", 0)
					body("data.myActiveTeam", nullValue())
				}
			}
		}

		context("초대를 2건 받은 유저") {
			it("receivedInvitationCount=2를 반환한다 (200)") {
				val me = 5002L
				persistMatchUser(me, Gender.MALE)
				repeat(2) { i: Int ->
					val ownerId: Long = 5200L + i
					val teamId: Long = persistTeam(TeamStatus.INVITING, Gender.MALE)
					persistMember(teamId, ownerId, TeamMemberStatus.ACTIVE)
					persistMember(teamId, me, TeamMemberStatus.INVITED)
				}

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.receivedInvitationCount", 2)
					body("data.recommendedTeams", hasSize<Any>(0))
					body("data.myActiveTeam", nullValue())
				}
			}
		}

		context("결성(ACTIVE) 팀에 속한 유저") {
			it("myActiveTeam에 teamId와 내/친구 profileImageCode를 반환한다 (200)") {
				val me = 5003L
				val friend = 5301L
				persistMatchUser(me, Gender.MALE, profileImageCode = "3")
				persistMatchUser(friend, Gender.MALE, profileImageCode = "7")
				val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.MALE)
				persistMember(teamId, me, TeamMemberStatus.ACTIVE)
				persistMember(teamId, friend, TeamMemberStatus.ACTIVE)

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.myActiveTeam.teamId", teamId.toInt())
					body("data.myActiveTeam.gender", "MALE")
					body("data.myActiveTeam.myProfileImageCode", "3")
					body("data.myActiveTeam.partnerProfileImageCode", "7")
					body("data.recommendedTeams", hasSize<Any>(0))
					body("data.receivedInvitationCount", 0)
				}
			}
		}

		context("결성(ACTIVE) 팀이 있고 진행 중 매칭이 있는 유저") {
			it("recommendedTeams에 추천 팀이 아니라 내 팀과 매칭된 상대 팀을 내려준다 (만료 매칭은 제외, 200)") {
				val me = 5005L
				val friend = 5305L
				persistMatchUser(me, Gender.MALE)
				persistMatchUser(friend, Gender.MALE)
				val myTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.MALE)
				persistMember(myTeamId, me, TeamMemberStatus.ACTIVE)
				persistMember(myTeamId, friend, TeamMemberStatus.ACTIVE)

				// 진행 중 매칭으로 묶인 상대 팀(표시 대상).
				val oppTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE)
				persistMember(oppTeamId, 5401L, TeamMemberStatus.ACTIVE)
				persistMember(oppTeamId, 5402L, TeamMemberStatus.ACTIVE)
				persistMatchUser(5401L, Gender.FEMALE)
				persistMatchUser(5402L, Gender.FEMALE)
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = 5401L, gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = 5402L, gender = Gender.FEMALE))
				val liveMatch: Long = persistTeamMatch("$myTeamId-$oppTeamId", expiresAt = LocalDateTime.of(2999, 1, 1, 0, 0))
				persistMatchedTeam(liveMatch, myTeamId)
				persistMatchedTeam(liveMatch, oppTeamId)

				// 만료된 매칭으로 묶인 상대 팀(제외 대상).
				val expiredOppTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE)
				val expiredMatch: Long = persistTeamMatch("$myTeamId-$expiredOppTeamId", expiresAt = LocalDateTime.of(2000, 1, 1, 0, 0))
				persistMatchedTeam(expiredMatch, myTeamId)
				persistMatchedTeam(expiredMatch, expiredOppTeamId)

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.recommendedTeams", hasSize<Any>(1))
					body("data.recommendedTeams[0].teamId", oppTeamId.toInt())
					body("data.recommendedTeams[0].members", hasSize<Any>(2))
					body("data.myActiveTeam.teamId", myTeamId.toInt())
					body("data.receivedInvitationCount", 0)
				}
			}
		}

		context("추천·초대·결성 팀이 모두 없는 유저") {
			it("recommendedTeams=[], count=0, myActiveTeam=null을 반환한다 (200)") {
				val me = 5004L
				persistMatchUser(me, Gender.MALE)

				get("/teams/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.recommendedTeams", hasSize<Any>(0))
					body("data.receivedInvitationCount", 0)
					body("data.myActiveTeam", nullValue())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
