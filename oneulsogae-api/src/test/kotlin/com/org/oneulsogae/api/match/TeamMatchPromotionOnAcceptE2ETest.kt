package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.RecommendedTeamEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.teammatch.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QMatchedTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QRecommendedTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMatchEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMatchEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `POST /teams/v1/{teamId}/acceptance` 수락으로 팀이 결성(ACTIVE)될 때,
 * 두 구성원에게 개인 추천됐던 팀(recommended_teams)이 결성된 팀과의 PROPOSED 팀 매칭으로 승격되는지 검증한다.
 */
class TeamMatchPromotionOnAcceptE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	fun persistFemaleTeam(status: TeamStatus): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "여성팀", gender = Gender.FEMALE, regionId = 1L, introduction = "즐겁게 만나요", status = status),
		)
		return team.id!!
	}

	fun persistRecommendation(userId: Long, teamId: Long) {
		IntegrationUtil.persist(RecommendedTeamEntityFixture.create(userId = userId, teamId = teamId))
	}

	// 같은 성별 두 솔로 유저로 팀을 결성(초대→수락)하고 teamId를 돌려준다. 추천 시드 후 호출해야 수락 시 승격이 일어난다.
	// 초대·수락 모두 회사 인증이 필요하므로 owner·invited 모두 인증된 프로필을 미리 채운다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = ownerId, gender = Gender.MALE, companyName = "오늘소개"))
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = invitedUserId, gender = Gender.MALE, companyName = "오늘소개"))
		return post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	fun acceptTeam(invitedUserId: Long, teamId: Long) {
		post("/teams/v1/$teamId/acceptance") {
			bearer(accessTokenFor(invitedUserId))
		} expect {
			status(200)
		}
	}

	describe("POST /teams/v1/{teamId}/acceptance — 추천 팀 승격") {

		context("두 구성원 모두 ACTIVE 추천 팀이 있으면") {
			it("각 추천 팀이 PROPOSED·RECOMMENDED 팀 매칭으로 승격된다 (2건, 양쪽 팀 WAITING)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val recTeamForOwner: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				val recTeamForInvited: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				persistRecommendation(ownerId, recTeamForOwner)
				persistRecommendation(invitedUserId, recTeamForInvited)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				val teamMatches: List<TeamMatchEntity> = allTeamMatches()
				teamMatches.size shouldBe 2
				teamMatches.all { it.status == MatchStatus.PROPOSED } shouldBe true
				teamMatches.all { it.matchType == TeamMatchType.RECOMMENDED } shouldBe true
				teamMatches.map { it.memberKey }.toSet() shouldBe setOf(
					listOf(teamId, recTeamForOwner).sorted().joinToString("-"),
					listOf(teamId, recTeamForInvited).sorted().joinToString("-"),
				)
				teamMatches.forEach { teamMatch: TeamMatchEntity ->
					val matched: List<MatchedTeamEntity> = matchedTeamsOf(teamMatch.id!!)
					matched.size shouldBe 2
					matched.all { it.status == MatchedTeamStatus.WAITING } shouldBe true
				}
			}
		}

		context("한 구성원에게만 추천 팀이 있으면") {
			it("팀 매칭이 1건만 생성된다") {
				val ownerId = 3011L
				val invitedUserId = 3012L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val recTeam: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				persistRecommendation(ownerId, recTeam)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				val teamMatches: List<TeamMatchEntity> = allTeamMatches()
				teamMatches.size shouldBe 1
				teamMatches[0].memberKey shouldBe listOf(teamId, recTeam).sorted().joinToString("-")
				teamMatches[0].status shouldBe MatchStatus.PROPOSED
				teamMatches[0].matchType shouldBe TeamMatchType.RECOMMENDED
			}
		}

		context("추천 팀이 ACTIVE가 아니면") {
			it("승격하지 않는다 (0건)") {
				val ownerId = 3021L
				val invitedUserId = 3022L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val deactivatedTeam: Long = persistFemaleTeam(TeamStatus.DEACTIVATED)
				persistRecommendation(ownerId, deactivatedTeam)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				allTeamMatches().size shouldBe 0
			}
		}

		context("두 구성원이 같은 팀을 추천받으면") {
			it("팀 매칭은 1건만 생성된다 (중복 제거)") {
				val ownerId = 3031L
				val invitedUserId = 3032L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val sharedRecTeam: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				persistRecommendation(ownerId, sharedRecTeam)
				persistRecommendation(invitedUserId, sharedRecTeam)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				val teamMatches: List<TeamMatchEntity> = allTeamMatches()
				teamMatches.size shouldBe 1
				teamMatches[0].memberKey shouldBe listOf(teamId, sharedRecTeam).sorted().joinToString("-")
			}
		}

		context("추천 팀과 이미 (과거 소개돼 종료·소프트삭제된) 팀 매칭 조합이 있으면") {
			it("승격을 건너뛰고 수락은 정상 처리된다 (200, ux_member_key 위반 5xx 없음)") {
				val ownerId = 3041L
				val invitedUserId = 3042L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val recTeam: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				persistRecommendation(ownerId, recTeam)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				// 결성될 팀(teamId)과 추천 팀(recTeam) 조합의 팀 매칭이 과거에 있었고 종료·소프트삭제된 상태로 시드한다.
				// ux_member_key는 deleted_at과 무관하므로, 이 조합을 다시 승격하려 하면 유니크 위반이 난다 — 승격은 이를 건너뛰어야 한다.
				val existingKey: String = listOf(teamId, recTeam).sorted().joinToString("-")
				IntegrationUtil.persist(
					TeamMatchEntity(
						memberKey = existingKey,
						introducedDate = LocalDate.of(2026, 1, 1),
						expiresAt = LocalDateTime.of(2026, 1, 1, 0, 0),
						status = MatchStatus.CLOSED,
						matchType = TeamMatchType.RECOMMENDED,
						dateInitAmount = 0,
						dateAcceptAmount = 0,
					).also { it.softDelete(LocalDateTime.of(2026, 1, 1, 0, 0)) },
				)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
				}

				// 새 팀 매칭은 승격되지 않는다. (소프트삭제된 기존 행은 @SQLRestriction으로 활성 조회에서 제외되어 0건)
				allTeamMatches().size shouldBe 0
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
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})

private fun allTeamMatches(): List<TeamMatchEntity> {
	val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().selectFrom(teamMatch).fetch()
}

private fun matchedTeamsOf(teamMatchId: Long): List<MatchedTeamEntity> {
	val matched: QMatchedTeamEntity = QMatchedTeamEntity.matchedTeamEntity
	return IntegrationUtil.getQuery().selectFrom(matched).where(matched.teamMatchId.eq(teamMatchId)).fetch()
}
