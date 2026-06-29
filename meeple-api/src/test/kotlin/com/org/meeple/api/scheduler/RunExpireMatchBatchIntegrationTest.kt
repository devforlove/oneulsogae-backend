package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.popup.PopupType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.infra.coin.command.entity.QCoinBalanceEntity
import com.org.meeple.infra.fixture.CoinBalanceEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.match.command.entity.SoloMatchEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.popup.command.entity.QPopupEntity
import com.org.meeple.scheduler.match.command.application.port.`in`.RunExpireMatchBatchUseCase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunExpireMatchBatchUseCase](ExpireMatchBatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 만료 조회가 올바른 행만 선별하고, 정리·환불·팝업을 끝까지 수행하며, 성사(MATCHED)는 건드리지 않음을 검증한다.
 */
class RunExpireMatchBatchIntegrationTest(
	private val runExpireMatchBatchUseCase: RunExpireMatchBatchUseCase,
) : AbstractIntegrationSupport({

	describe("run") {
		it("만료 배치를 두 번 실행해도 환불은 한 번만 이뤄진다 (멱등성)") {
			val past: LocalDateTime = LocalDateTime.now().minusHours(1)
			val applicantId = 1501L
			val expiredSolo: SoloMatchEntity = IntegrationUtil.persist(
				SoloMatchEntityFixture.create(memberKey = "1501-2501", status = MatchStatus.PARTIALLY_ACCEPTED, expiresAt = past),
			)
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = expiredSolo.id!!, userId = applicantId, gender = Gender.MALE, status = MatchMemberStatus.APPLY))
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = expiredSolo.id!!, userId = 2501L, gender = Gender.FEMALE, status = MatchMemberStatus.WAITING))
			IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = applicantId, balance = 100))

			runExpireMatchBatchUseCase.run()
			runExpireMatchBatchUseCase.run()

			coinBalanceOf(applicantId) shouldBe 116
			popupCountOf(applicantId, PopupType.MATCH_FAILED_REFUND) shouldBe 1
		}

		it("만료된 PARTIALLY_ACCEPTED 솔로·팀은 정리·환불하고, 성사(MATCHED)·미만료는 그대로 둔다") {
			val past: LocalDateTime = LocalDateTime.now().minusHours(1)
			val future: LocalDateTime = LocalDateTime.now().plusHours(1)

			// (1) 만료 솔로 PARTIALLY_ACCEPTED — 정리 + 16 환불 + 소개팅 팝업
			val soloApplicant = 1001L
			val expiredSolo: SoloMatchEntity = IntegrationUtil.persist(
				SoloMatchEntityFixture.create(memberKey = "1001-2001", status = MatchStatus.PARTIALLY_ACCEPTED, expiresAt = past),
			)
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = expiredSolo.id!!, userId = soloApplicant, gender = Gender.MALE, status = MatchMemberStatus.APPLY))
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = expiredSolo.id!!, userId = 2001L, gender = Gender.FEMALE, status = MatchMemberStatus.WAITING))
			IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = soloApplicant, balance = 100))

			// (2) 성사(MATCHED) 솔로 — 무변경 (만료 시각이 과거여도 상태로 제외)
			val matchedSolo: SoloMatchEntity = IntegrationUtil.persist(
				SoloMatchEntityFixture.create(memberKey = "1003-2003", status = MatchStatus.MATCHED, expiresAt = past),
			)
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchedSolo.id!!, userId = 1003L, gender = Gender.MALE, status = MatchMemberStatus.ACTIVE))

			// (3) 미만료 PARTIALLY_ACCEPTED 솔로 — 무변경
			val freshSolo: SoloMatchEntity = IntegrationUtil.persist(
				SoloMatchEntityFixture.create(memberKey = "1004-2004", status = MatchStatus.PARTIALLY_ACCEPTED, expiresAt = future),
			)
			IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = freshSolo.id!!, userId = 1004L, gender = Gender.MALE, status = MatchMemberStatus.APPLY))

			// (4) 만료 팀 PARTIALLY_ACCEPTED — 정리 + 20 환불 + 미팅 팝업
			val teamApplicant = 3001L
			val expiredTeam: TeamMatchEntity = IntegrationUtil.persist(
				TeamMatchEntity(
					memberKey = MatchedTeams.of(listOf(10L, 20L)).memberKey(),
					introducedDate = LocalDate.now(),
					expiresAt = past,
					status = MatchStatus.PARTIALLY_ACCEPTED,
					matchType = TeamMatchType.DAILY,
					dateInitAmount = 40,
					dateAcceptAmount = 40,
				),
			)
			IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = expiredTeam.id!!, teamId = 10L, status = MatchedTeamStatus.APPLY, applicantUserId = teamApplicant))
			IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = expiredTeam.id!!, teamId = 20L, status = MatchedTeamStatus.WAITING))
			IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = teamApplicant, balance = 100))

			runExpireMatchBatchUseCase.run()

			// 만료 솔로: 정리 + 환불 + 팝업
			soloMatchById(expiredSolo.id!!).shouldBeNull()
			coinBalanceOf(soloApplicant) shouldBe 116
			popupExists(soloApplicant, PopupType.MATCH_FAILED_REFUND) shouldBe true

			// 성사 솔로·미만료 솔로: 그대로
			soloMatchById(matchedSolo.id!!).shouldNotBeNull()
			soloMatchById(freshSolo.id!!).shouldNotBeNull()

			// 만료 팀: 정리 + 환불 + 팝업
			teamMatchById(expiredTeam.id!!).shouldBeNull()
			coinBalanceOf(teamApplicant) shouldBe 120
			popupExists(teamApplicant, PopupType.MEETING_FAILED_REFUND) shouldBe true
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
	}
})

private fun soloMatchById(id: Long): SoloMatchEntity? {
	val q = QSoloMatchEntity.soloMatchEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.id.eq(id)).fetchOne()
}

private fun teamMatchById(id: Long): TeamMatchEntity? {
	val q = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.id.eq(id)).fetchOne()
}

private fun coinBalanceOf(userId: Long): Int {
	val q = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery().select(q.balance).from(q).where(q.userId.eq(userId)).fetchOne()!!
}

private fun popupExists(userId: Long, type: PopupType): Boolean {
	val q = QPopupEntity.popupEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.userId.eq(userId).and(q.popUpType.eq(type))).fetchFirst() != null
}

private fun popupCountOf(userId: Long, type: PopupType): Int {
	val q = QPopupEntity.popupEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.userId.eq(userId).and(q.popUpType.eq(type))).fetch().size
}
