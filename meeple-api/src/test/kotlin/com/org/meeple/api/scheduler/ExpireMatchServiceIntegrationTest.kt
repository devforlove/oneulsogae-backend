package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.popup.PopupType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.`in`.ExpireMatchUseCase
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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [ExpireMatchUseCase](ExpireMatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 매치 1건의 soft-delete + 코인 환불 + 팝업 생성을 직접 검증한다.
 */
class ExpireMatchServiceIntegrationTest(
	private val expireMatchUseCase: ExpireMatchUseCase,
) : AbstractIntegrationSupport({

	describe("expireSoloMatch") {
		context("한쪽만 신청(APPLY)한 PARTIALLY_ACCEPTED 솔로 매칭은") {
			it("soft-delete하고 신청자에게 16코인 환불 + 소개팅 환불 팝업을 만든다") {
				val applicantId = 1001L
				val partnerId = 2001L
				val header: SoloMatchEntity = IntegrationUtil.persist(
					SoloMatchEntityFixture.create(memberKey = "1001-2001", status = MatchStatus.PARTIALLY_ACCEPTED),
				)
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = applicantId, gender = Gender.MALE, status = MatchMemberStatus.APPLY))
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = partnerId, gender = Gender.FEMALE, status = MatchMemberStatus.WAITING))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = applicantId, balance = 100))

				expireMatchUseCase.expireSoloMatch(header.id!!)

				soloMatchById(header.id!!).shouldBeNull()
				coinBalanceOf(applicantId) shouldBe 116
				popupExists(applicantId, PopupType.MATCH_FAILED_REFUND) shouldBe true
			}
		}

		context("아무도 신청하지 않은 PROPOSED 솔로 매칭은") {
			it("soft-delete만 하고 환불·팝업이 없다") {
				val a = 1101L
				val b = 2101L
				val header: SoloMatchEntity = IntegrationUtil.persist(
					SoloMatchEntityFixture.create(memberKey = "1101-2101", status = MatchStatus.PROPOSED),
				)
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = a, gender = Gender.MALE, status = MatchMemberStatus.WAITING))
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = header.id!!, userId = b, gender = Gender.FEMALE, status = MatchMemberStatus.WAITING))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = a, balance = 100))

				expireMatchUseCase.expireSoloMatch(header.id!!)

				soloMatchById(header.id!!).shouldBeNull()
				coinBalanceOf(a) shouldBe 100
				popupExists(a, PopupType.MATCH_FAILED_REFUND) shouldBe false
			}
		}
	}

	describe("expireTeamMatch") {
		context("한쪽 팀만 신청(APPLY)한 PARTIALLY_ACCEPTED 팀 매칭은") {
			it("soft-delete하고 지불자에게 20코인 환불 + 미팅 환불 팝업을 만든다") {
				val applicantId = 3001L
				val teamAId = 10L
				val teamBId = 20L
				val header: TeamMatchEntity = IntegrationUtil.persist(
					TeamMatchEntity(
						memberKey = MatchedTeams.of(listOf(teamAId, teamBId)).memberKey(),
						introducedDate = LocalDate.now(),
						expiresAt = LocalDateTime.now().minusHours(1),
						status = MatchStatus.PARTIALLY_ACCEPTED,
						matchType = TeamMatchType.DAILY,
						dateInitAmount = 40,
						dateAcceptAmount = 40,
					),
				)
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamAId, status = MatchedTeamStatus.APPLY, applicantUserId = applicantId))
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = header.id!!, teamId = teamBId, status = MatchedTeamStatus.WAITING))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = applicantId, balance = 100))

				expireMatchUseCase.expireTeamMatch(header.id!!)

				teamMatchById(header.id!!).shouldBeNull()
				coinBalanceOf(applicantId) shouldBe 120
				popupExists(applicantId, PopupType.MEETING_FAILED_REFUND) shouldBe true
			}
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
