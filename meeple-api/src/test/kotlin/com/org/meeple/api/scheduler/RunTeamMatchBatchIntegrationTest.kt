package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.scheduler.match.command.application.port.`in`.RunTeamMatchBatchUseCase
import com.org.meeple.scheduler.match.command.domain.TeamMatchBatchResult
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunTeamMatchBatchUseCase](TeamMatchBatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 배치가 시작 시 regionProximityPort.refresh()로 근접 스냅샷을 적재하므로, regions·teams·team_members·match_user를 적재한 뒤 호출한다.
 * RegionShuffler는 TestRegionShufflerConfig가 항등(순서 유지)으로 고정 → 근접 우선이 결정적.
 */
class RunTeamMatchBatchIntegrationTest(
	private val runTeamMatchBatchUseCase: RunTeamMatchBatchUseCase,
) : AbstractIntegrationSupport({

	describe("run") {

		context("반대 성별·가까운 권역의 ACTIVE 팀 둘(각 구성원 2주 내 로그인)이 있으면") {
			it("두 팀을 PROPOSED(DAILY)로 소개한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7001L)
				val femaleTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId, memberUserId = 7002L)

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				result.recommended shouldBe 1
				result.failed shouldBe 0
				val teamMatch: TeamMatchEntity = proposedTeamMatchBetween(maleTeamId, femaleTeamId).shouldNotBeNull()
				teamMatch.matchType shouldBe TeamMatchType.DAILY
				teamMatch.introducedDate shouldBe LocalDate.now()
			}
		}

		context("반대 성별 후보 팀이 없으면") {
			it("아무 팀도 소개하지 못한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7001L)
				val otherMaleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7002L)

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedTeamMatchBetween(maleTeamId, otherMaleTeamId).shouldBeNull()
			}
		}

		context("구성원이 최근 2주 내 로그인하지 않은 팀은") {
			it("후보에서 제외되어 소개되지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7001L)
				// 여성 팀 구성원은 3주 전 로그인 → "한 명이라도 2주 내 로그인" 조건 미충족
				val staleFemaleTeamId: Long = persistActiveTeam(
					gender = Gender.FEMALE, regionId = regionId, memberUserId = 7002L,
					lastLoginAt = LocalDateTime.now().minusWeeks(3),
				)

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedTeamMatchBetween(maleTeamId, staleFemaleTeamId).shouldBeNull()
			}
		}

		context("결성(ACTIVE) 상태가 아닌 팀은") {
			it("후보에서 제외되어 소개되지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7001L)
				// 여성 팀은 아직 초대중(INVITING) → ACTIVE 아님
				val invitingFemaleTeamId: Long = persistTeam(
					gender = Gender.FEMALE, regionId = regionId, memberUserId = 7002L, status = TeamStatus.INVITING,
				)

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedTeamMatchBetween(maleTeamId, invitingFemaleTeamId).shouldBeNull()
			}
		}

		context("가까운 권역과 먼 권역에 후보 팀이 있으면") {
			it("가까운 권역의 팀과 소개한다") {
				val nearRegionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val farRegionId: Long = persistRegion("부산광역시", "해운대구", 35.16, 129.16)
				// 남성 팀이 가장 최근 로그인 → 최신 로그인순으로 먼저 처리됨 → 근접 권역 여성 팀과 매칭
				val now: LocalDateTime = LocalDateTime.now()
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = nearRegionId, memberUserId = 7001L, lastLoginAt = now)
				val nearFemaleTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = nearRegionId, memberUserId = 7002L, lastLoginAt = now.minusMinutes(1))
				val farFemaleTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = farRegionId, memberUserId = 7003L, lastLoginAt = now.minusMinutes(2))

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				result.recommended shouldBe 1
				proposedTeamMatchBetween(maleTeamId, nearFemaleTeamId).shouldNotBeNull()
				proposedTeamMatchBetween(maleTeamId, farFemaleTeamId).shouldBeNull()
			}
		}

		context("이미 성사(MATCHED)된 팀은") {
			it("신규 소개 대상·후보에서 제외된다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7001L)
				val femaleTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId, memberUserId = 7002L)
				// maleTeam이 다른 팀(9001)과 과거에 성사(MATCHED)됨. 과거 일자라 '오늘 소개' 제외엔 안 걸린다.
				persistTeamMatch(maleTeamId, 9001L, status = MatchStatus.MATCHED, introducedDate = LocalDate.now().minusDays(3))

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedTeamMatchBetween(maleTeamId, femaleTeamId).shouldBeNull()
			}
		}

		context("오늘 이미 소개된 팀은") {
			it("재실행해도 신규 소개 대상·후보에서 제외된다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7001L)
				val femaleTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId, memberUserId = 7002L)
				// maleTeam이 오늘 다른 팀(9001)과 소개됨(introduced_date = today)
				persistTeamMatch(maleTeamId, 9001L, status = MatchStatus.PROPOSED, introducedDate = LocalDate.now())

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedTeamMatchBetween(maleTeamId, femaleTeamId).shouldBeNull()
			}
		}

		context("과거 소개됐다가 취소(소프트 삭제)된 팀 쌍은") {
			it("member_key가 남아 재소개 시 충돌하므로, 우회 조회로 이력으로 인정해 다시 소개하지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleTeamId: Long = persistActiveTeam(gender = Gender.MALE, regionId = regionId, memberUserId = 7001L)
				val femaleTeamId: Long = persistActiveTeam(gender = Gender.FEMALE, regionId = regionId, memberUserId = 7002L)
				// 7001팀-7002팀은 과거 소개됐다가 취소돼 소프트 삭제됨. ux_member_key는 deleted_at 미포함이라 member_key가 그대로 남는다.
				persistCancelledPastTeamMatch(maleTeamId, femaleTeamId)

				val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()

				// 우회 조회로 이력을 인정해 재소개를 막으면 유니크 충돌 없음(우회 안 하면 INSERT가 member_key 충돌로 failed).
				result.recommended shouldBe 0
				result.failed shouldBe 0
				proposedTeamMatchBetween(maleTeamId, femaleTeamId).shouldBeNull()
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
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

private fun persistActiveTeam(gender: Gender, regionId: Long, memberUserId: Long, lastLoginAt: LocalDateTime = LocalDateTime.now()): Long =
	persistTeam(gender = gender, regionId = regionId, memberUserId = memberUserId, status = TeamStatus.ACTIVE, lastLoginAt = lastLoginAt)

private fun persistTeam(gender: Gender, regionId: Long, memberUserId: Long, status: TeamStatus, lastLoginAt: LocalDateTime = LocalDateTime.now()): Long {
	val team: TeamEntity = IntegrationUtil.persist(
		TeamEntity(name = "팀", gender = gender, regionId = regionId, introduction = "소개", status = status),
	)
	val teamId: Long = team.id!!
	IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = memberUserId, status = TeamMemberStatus.ACTIVE))
	IntegrationUtil.persist(MatchUserEntityFixture.create(userId = memberUserId, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt))
	return teamId
}

private fun persistTeamMatch(teamAId: Long, teamBId: Long, status: MatchStatus, introducedDate: LocalDate) {
	val header: TeamMatchEntity = IntegrationUtil.persist(
		TeamMatchEntity(
			memberKey = MatchedTeams.of(listOf(teamAId, teamBId)).memberKey(),
			introducedDate = introducedDate,
			expiresAt = LocalDateTime.now().plusDays(1),
			status = status,
			matchType = TeamMatchType.DAILY,
			dateInitAmount = 0,
			dateAcceptAmount = 0,
		),
	)
	// 성사(MATCHED) 매칭의 참가 팀은 ACTIVE, 그 외(PROPOSED 등)는 WAITING.
	val memberStatus: MatchedTeamStatus = if (status == MatchStatus.MATCHED) MatchedTeamStatus.ACTIVE else MatchedTeamStatus.WAITING
	IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamAId, status = memberStatus))
	IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamBId, status = memberStatus))
}

// [teamAId]가 과거 [teamBId]와 소개됐다가 취소해 헤더·참가 팀이 소프트 삭제된 상태를 만든다(과거 일자).
// ux_member_key는 deleted_at을 포함하지 않아 member_key가 그대로 남으므로, 재소개 시 충돌한다 → existsByPair가 이 삭제 행을 봐야 한다.
private fun persistCancelledPastTeamMatch(teamAId: Long, teamBId: Long) {
	val header: TeamMatchEntity = IntegrationUtil.persist(
		TeamMatchEntity(
			memberKey = MatchedTeams.of(listOf(teamAId, teamBId)).memberKey(),
			introducedDate = LocalDate.now().minusDays(3),
			expiresAt = LocalDateTime.now().plusDays(1),
			status = MatchStatus.CLOSED,
			matchType = TeamMatchType.DAILY,
			dateInitAmount = 0,
			dateAcceptAmount = 0,
		).apply { softDelete() },
	)
	IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamAId, status = MatchedTeamStatus.DEACTIVE).apply { softDelete() })
	IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamBId, status = MatchedTeamStatus.DEACTIVE).apply { softDelete() })
}

private fun proposedTeamMatchBetween(teamAId: Long, teamBId: Long): TeamMatchEntity? {
	val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery()
		.selectFrom(teamMatch)
		.where(
			teamMatch.memberKey.eq(MatchedTeams.of(listOf(teamAId, teamBId)).memberKey())
				.and(teamMatch.status.eq(MatchStatus.PROPOSED)),
		)
		.fetchOne()
}
