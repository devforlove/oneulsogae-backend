package com.org.meeple.api.admin

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.report.ReportStatus
import com.org.meeple.infra.fixture.CoinHistoryEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.ReportEntityFixture
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.TeamMatchEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.coin.command.entity.QCoinHistoryEntity
import com.org.meeple.infra.report.command.entity.QReportEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMatchEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDateTime

/**
 * `GET /admin/v1/dashboard` E2E 테스트.
 * 전체 사용자·금일 가입자(created_at)·금일 DAU(last_login_at)·금일 코인 결제액(PURCHASE 적립 합)을 검증한다.
 * (인가 규칙(401/403)은 AdminAccessE2ETest가 /admin 하위 경로 공통으로 검증한다)
 */
class AdminDashboardE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/dashboard") {

		it("전체/금일 지표를 한 번에 반환한다 (200)") {
			val now: LocalDateTime = LocalDateTime.now()
			val yesterday: LocalDateTime = now.minusDays(1)

			// 오늘 가입 + 오늘 로그인(DAU), 오늘 가입 + 어제 로그인, 어제 가입(created_at 소급) + 미로그인.
			IntegrationUtil.persist(UserEntityFixture.create(providerId = "dash-1", lastLoginAt = now))
			IntegrationUtil.persist(UserEntityFixture.create(providerId = "dash-2", lastLoginAt = yesterday))
			val oldUserId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "dash-3")).id!!
			val user: QUserEntity = QUserEntity.userEntity
			IntegrationUtil.update { queryFactory: JPAQueryFactory ->
				queryFactory.update(user).set(user.createdAt, yesterday).where(user.id.eq(oldUserId)).execute()
			}

			// 결제액은 금일 PURCHASE만 합산돼야 한다. (어제 결제·무료 적립은 제외)
			IntegrationUtil.persist(CoinHistoryEntityFixture.create(userId = 1L, amount = 100, coinGetType = CoinGetType.PURCHASE, occurredAt = now))
			IntegrationUtil.persist(CoinHistoryEntityFixture.create(userId = 1L, amount = 50, coinGetType = CoinGetType.PURCHASE, occurredAt = yesterday))
			IntegrationUtil.persist(CoinHistoryEntityFixture.create(userId = 1L, amount = 10, coinGetType = CoinGetType.DAILY, occurredAt = now))

			// 진행중 매치는 PROPOSED·PARTIALLY_ACCEPTED만 센다. (MATCHED·CLOSED 제외)
			IntegrationUtil.persist(SoloMatchEntityFixture.create(memberKey = "d-1", status = MatchStatus.PROPOSED))
			IntegrationUtil.persist(SoloMatchEntityFixture.create(memberKey = "d-2", status = MatchStatus.PARTIALLY_ACCEPTED))
			IntegrationUtil.persist(SoloMatchEntityFixture.create(memberKey = "d-3", status = MatchStatus.MATCHED))
			IntegrationUtil.persist(TeamMatchEntityFixture.create(memberKey = "d-4", status = MatchStatus.PROPOSED))
			IntegrationUtil.persist(TeamMatchEntityFixture.create(memberKey = "d-5", status = MatchStatus.CLOSED))

			// 미처리 신고는 status = PENDING만 센다.
			IntegrationUtil.persist(ReportEntityFixture.create(fromUserId = 1L))
			IntegrationUtil.persist(ReportEntityFixture.create(fromUserId = 2L))
			IntegrationUtil.persist(ReportEntityFixture.create(fromUserId = 3L, status = ReportStatus.RESOLVED))

			get("/admin/v1/dashboard") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalUsers", 3)
				body("data.todaySignups", 2)
				body("data.todayActiveUsers", 1)
				body("data.todayCoinPurchaseAmount", 100)
				body("data.ongoingSoloMatches", 2)
				body("data.ongoingTeamMatches", 1)
				body("data.pendingReports", 2)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QReportEntity.reportEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
