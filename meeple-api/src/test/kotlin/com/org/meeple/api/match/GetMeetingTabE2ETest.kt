package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RecommendedTeamEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.teammatch.command.entity.MatchedTeamEntity
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.teammatch.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMatchEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.meeple.infra.teammatch.command.entity.TeamEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMatchEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `GET /team-matches/v1/meeting-tab` E2E 테스트. (미팅탭 화면 집계)
 * 추천 팀 목록(recommendedTeams, 없으면 빈 리스트) + 받은 초대 개수(receivedInvitationCount) + 내 결성 팀(myTeam, 없으면 null)을 한 번에 반환한다.
 */
class GetMeetingTabE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(
		userId: Long,
		gender: Gender = Gender.MALE,
		profileImageCode: String = "1",
		lastLoginAt: LocalDateTime = LocalDateTime.now(),
	) {
		IntegrationUtil.persist(
			MatchUserEntityFixture.create(userId = userId, gender = gender, profileImageCode = profileImageCode, lastLoginAt = lastLoginAt),
		)
	}

	fun persistTeam(status: TeamStatus, gender: Gender, regionId: Long = 1L): Long =
		IntegrationUtil.persist(
			TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "함께 즐겁게 활동해요", status = status),
		).id!!

	fun persistMember(teamId: Long, userId: Long, status: TeamMemberStatus) {
		IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = userId, status = status))
	}

	fun persistTeamMatch(
		memberKey: String,
		expiresAt: LocalDateTime,
		status: MatchStatus = MatchStatus.PROPOSED,
		dateInitAmount: Int = 40,
		dateAcceptAmount: Int = 40,
	): Long =
		IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = memberKey,
				introducedDate = LocalDate.of(2026, 6, 24),
				expiresAt = expiresAt,
				status = status,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = dateInitAmount,
				dateAcceptAmount = dateAcceptAmount,
			),
		).id!!

	fun persistMatchedTeam(teamMatchId: Long, teamId: Long, status: MatchedTeamStatus = MatchedTeamStatus.WAITING) {
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamId, status = status))
	}

	describe("GET /team-matches/v1/meeting-tab") {

		context("추천 팀이 적재된 솔로 유저") {
			it("recommendedTeams에 팀·팀원 프로필을, count=0·myTeam=null로 반환한다 (200)") {
				val soloUserId = 5001L
				persistMatchUser(soloUserId, Gender.MALE)
				val gangnamId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!
				val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE, regionId = gangnamId)
				persistMember(teamId, 5101L, TeamMemberStatus.ACTIVE)
				persistMember(teamId, 5102L, TeamMemberStatus.ACTIVE)
				// 팀 lastLoginAt은 구성원 중 최댓값이어야 한다. 5102가 더 최근이라 그 값이 나온다.
				persistMatchUser(5101L, Gender.FEMALE, lastLoginAt = LocalDateTime.of(2026, 6, 20, 10, 0))
				persistMatchUser(5102L, Gender.FEMALE, lastLoginAt = LocalDateTime.of(2026, 6, 25, 15, 30))
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

				get("/team-matches/v1/meeting-tab") {
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
					// 팀 최근 로그인은 구성원(6/20, 6/25) 중 최댓값
					body("data.recommendedTeams[0].lastLoginAt", startsWith("2026-06-25T15:30"))
					// 팀에 관심을 보낼 때 드는 코인 비용(MEETING_INIT/ACCEPT = 40)
					body("data.recommendedTeams[0].datingInitAmount", 40)
					body("data.recommendedTeams[0].datingAcceptAmount", 40)
					// 순수 추천 팀은 아직 매칭이 없어 상태 null·양 팀 관심 false
					// 순수 추천 팀은 아직 매칭이 없어 teamMatchId도 null이다.
					body("data.recommendedTeams[0].teamMatchId", nullValue())
					body("data.recommendedTeams[0].teamMatchStatus", nullValue())
					body("data.recommendedTeams[0].teamMatchExpiresAt", nullValue())
					body("data.recommendedTeams[0].hasUserInterest", false)
					body("data.recommendedTeams[0].hasPartnerInterest", false)
					body("data.receivedInvitationCount", 0)
					body("data.myTeam", nullValue())
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

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.receivedInvitationCount", 2)
					body("data.recommendedTeams", hasSize<Any>(0))
					body("data.myTeam", nullValue())
				}
			}
		}

		context("결성(ACTIVE) 팀에 속한 유저") {
			it("myTeam에 teamId와 내/친구 profileImageCode를 반환한다 (200)") {
				val me = 5003L
				val friend = 5301L
				persistMatchUser(me, Gender.MALE, profileImageCode = "3")
				persistMatchUser(friend, Gender.MALE, profileImageCode = "7")
				val teamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.MALE)
				persistMember(teamId, me, TeamMemberStatus.ACTIVE)
				persistMember(teamId, friend, TeamMemberStatus.ACTIVE)

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.myTeam.teamId", teamId.toInt())
					body("data.myTeam.status", "ACTIVE")
					body("data.myTeam.gender", "MALE")
					body("data.myTeam.myProfileImageCode", "3")
					body("data.myTeam.partnerProfileImageCode", "7")
					body("data.recommendedTeams", hasSize<Any>(0))
					body("data.receivedInvitationCount", 0)
				}
			}
		}

		context("내가 만든 초대중(INVITING) 팀이 있는 유저") {
			it("myTeam을 반환하고, 카드 슬롯은 매칭이 아닌 추천 팀 경로를 탄다 (200)") {
				val me = 5006L
				val invitee = 5306L
				persistMatchUser(me, Gender.MALE, profileImageCode = "4")
				persistMatchUser(invitee, Gender.MALE, profileImageCode = "8")
				// INVITING 팀: 요청자(나)는 ACTIVE 구성원(초대자), 초대 대상은 INVITED.
				val teamId: Long = persistTeam(TeamStatus.INVITING, Gender.MALE)
				persistMember(teamId, me, TeamMemberStatus.ACTIVE)
				persistMember(teamId, invitee, TeamMemberStatus.INVITED)

				// 나에게 추천된 ACTIVE 팀. INVITING 팀은 결성 전이라 매칭 경로가 아니라 추천 경로를 타야 이 팀이 보인다.
				val recommendedTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE)
				persistMember(recommendedTeamId, 5501L, TeamMemberStatus.ACTIVE)
				persistMember(recommendedTeamId, 5502L, TeamMemberStatus.ACTIVE)
				persistMatchUser(5501L, Gender.FEMALE)
				persistMatchUser(5502L, Gender.FEMALE)
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = 5501L, gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = 5502L, gender = Gender.FEMALE))
				IntegrationUtil.persist(
					RecommendedTeamEntityFixture.create(userId = me, teamId = recommendedTeamId, recommendedDate = LocalDate.of(2026, 6, 22)),
				)

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.myTeam.teamId", teamId.toInt())
					body("data.myTeam.status", "INVITING")
					body("data.myTeam.gender", "MALE")
					body("data.myTeam.myProfileImageCode", "4")
					body("data.myTeam.partnerProfileImageCode", "8")
					// INVITING이라 추천 경로 → 추천된 팀이 카드로 보인다. (매칭 경로였다면 team_match가 없어 빈 리스트)
					body("data.recommendedTeams", hasSize<Any>(1))
					body("data.recommendedTeams[0].teamId", recommendedTeamId.toInt())
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
				// 비용은 team_matches(DB)에서 내려오므로, 상수 기본값(40)과 다른 값으로 저장해 DB 조회임을 검증한다.
				// 상대 팀만 신청(APPLY)한 PARTIALLY_ACCEPTED 상태 → 내 팀 관심 false, 상대 팀 관심 true.
				val liveMatch: Long = persistTeamMatch(
					"$myTeamId-$oppTeamId",
					expiresAt = LocalDateTime.of(2999, 1, 1, 0, 0),
					status = MatchStatus.PARTIALLY_ACCEPTED,
					dateInitAmount = 55,
					dateAcceptAmount = 65,
				)
				persistMatchedTeam(liveMatch, myTeamId, MatchedTeamStatus.WAITING)
				persistMatchedTeam(liveMatch, oppTeamId, MatchedTeamStatus.APPLY)

				// 만료된 매칭으로 묶인 상대 팀(제외 대상).
				val expiredOppTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE)
				val expiredMatch: Long = persistTeamMatch("$myTeamId-$expiredOppTeamId", expiresAt = LocalDateTime.of(2000, 1, 1, 0, 0))
				persistMatchedTeam(expiredMatch, myTeamId)
				persistMatchedTeam(expiredMatch, expiredOppTeamId)

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.recommendedTeams", hasSize<Any>(1))
					body("data.recommendedTeams[0].teamId", oppTeamId.toInt())
					body("data.recommendedTeams[0].members", hasSize<Any>(2))
					// 매칭 상대 팀 경로에서도 구성원 최근 로그인이 채워진다.
					body("data.recommendedTeams[0].lastLoginAt", notNullValue())
					// 비용은 team_matches(DB)에서 조회한 값(55/65)이 그대로 내려온다.
					body("data.recommendedTeams[0].datingInitAmount", 55)
					body("data.recommendedTeams[0].datingAcceptAmount", 65)
					// 매칭된 상대 팀이라 관심 보내기 호출용 teamMatchId가 실린다.
					body("data.recommendedTeams[0].teamMatchId", liveMatch.toInt())
					// 헤더 상태 + 양 팀 관심 여부(상대만 APPLY → 내 팀 false, 상대 true)
					body("data.recommendedTeams[0].teamMatchStatus", MatchStatus.PARTIALLY_ACCEPTED.name)
					// 만료 시각도 team_matches(DB)에서 그대로 내려온다. (2999-01-01T00:00…)
					body("data.recommendedTeams[0].teamMatchExpiresAt", startsWith("2999-01-01T00:00"))
					body("data.recommendedTeams[0].hasUserInterest", false)
					body("data.recommendedTeams[0].hasPartnerInterest", true)
					body("data.myTeam.teamId", myTeamId.toInt())
					body("data.receivedInvitationCount", 0)
				}
			}
		}

		context("결성(ACTIVE) 팀에 서로 다른 상태의 진행 중 매칭이 여러 개 있으면") {
			it("매칭된 상대 팀을 성사(MATCHED), 상대 수락 대기(PARTIALLY_ACCEPTED), 소개됨(PROPOSED) 순으로 내려준다 (200)") {
				val me = 5007L
				val friend = 5307L
				persistMatchUser(me, Gender.MALE)
				persistMatchUser(friend, Gender.MALE)
				val myTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.MALE)
				persistMember(myTeamId, me, TeamMemberStatus.ACTIVE)
				persistMember(myTeamId, friend, TeamMemberStatus.ACTIVE)

				// 내 팀과 주어진 상태로 진행 중 매칭된 상대 팀 한 건을 만들고 상대 팀 id를 반환한다.
				fun seedMatchedOpponent(memberBase: Long, status: MatchStatus): Long {
					val oppTeamId: Long = persistTeam(TeamStatus.ACTIVE, Gender.FEMALE)
					persistMember(oppTeamId, memberBase, TeamMemberStatus.ACTIVE)
					persistMember(oppTeamId, memberBase + 1, TeamMemberStatus.ACTIVE)
					persistMatchUser(memberBase, Gender.FEMALE)
					persistMatchUser(memberBase + 1, Gender.FEMALE)
					IntegrationUtil.persist(UserDetailEntityFixture.create(userId = memberBase, gender = Gender.FEMALE))
					IntegrationUtil.persist(UserDetailEntityFixture.create(userId = memberBase + 1, gender = Gender.FEMALE))
					val teamMatchId: Long = persistTeamMatch(
						"$myTeamId-$oppTeamId",
						expiresAt = LocalDateTime.of(2999, 1, 1, 0, 0),
						status = status,
					)
					persistMatchedTeam(teamMatchId, myTeamId, MatchedTeamStatus.WAITING)
					persistMatchedTeam(teamMatchId, oppTeamId, MatchedTeamStatus.WAITING)
					return oppTeamId
				}

				// 상태 우선순위 정렬을 보이기 위해 요청 순서와 무관하게 시딩한다.
				val proposedOppTeamId: Long = seedMatchedOpponent(5601L, MatchStatus.PROPOSED)
				val partiallyAcceptedOppTeamId: Long = seedMatchedOpponent(5611L, MatchStatus.PARTIALLY_ACCEPTED)
				val matchedOppTeamId: Long = seedMatchedOpponent(5621L, MatchStatus.MATCHED)

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.recommendedTeams", hasSize<Any>(3))
					body("data.recommendedTeams.teamMatchStatus", contains("MATCHED", "PARTIALLY_ACCEPTED", "PROPOSED"))
					body("data.recommendedTeams.teamId", contains(matchedOppTeamId.toInt(), partiallyAcceptedOppTeamId.toInt(), proposedOppTeamId.toInt()))
				}
			}
		}

		context("추천·초대·결성 팀이 모두 없는 유저") {
			it("recommendedTeams=[], count=0, myTeam=null을 반환한다 (200)") {
				val me = 5004L
				persistMatchUser(me, Gender.MALE)

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.recommendedTeams", hasSize<Any>(0))
					body("data.receivedInvitationCount", 0)
					body("data.myTeam", nullValue())
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
