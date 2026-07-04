package com.org.meeple.infra.admin.query

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.core.admin.query.dao.GetAdminDashboardDao
import com.org.meeple.core.admin.query.dto.AdminDashboardView
import com.org.meeple.infra.coin.command.entity.QCoinHistoryEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetAdminDashboardDao]의 구현체. users·coin_history를 집계해 read model로 투영한다.
 * 어드민 대시보드는 호출 빈도가 낮아 count/sum 풀스캔을 수용한다. (지표용 인덱스는 두지 않는다 —
 * 데이터가 커져 느려지면 그때 일 단위 통계 적재로 전환을 검토한다)
 */
@Component
class GetAdminDashboardDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminDashboardDao {

	override fun load(todayStart: LocalDateTime): AdminDashboardView {
		val user: QUserEntity = QUserEntity.userEntity
		val coinHistory: QCoinHistoryEntity = QCoinHistoryEntity.coinHistoryEntity

		val totalUsers: Long = queryFactory
			.select(user.count())
			.from(user)
			.fetchOne() ?: 0L

		val todaySignups: Long = queryFactory
			.select(user.count())
			.from(user)
			.where(user.createdAt.goe(todayStart))
			.fetchOne() ?: 0L

		val todayActiveUsers: Long = queryFactory
			.select(user.count())
			.from(user)
			.where(user.lastLoginAt.goe(todayStart))
			.fetchOne() ?: 0L

		val todayCoinPurchaseAmount: Long = queryFactory
			.select(coinHistory.amount.sumLong())
			.from(coinHistory)
			.where(
				coinHistory.coinGetType.eq(CoinGetType.PURCHASE),
				coinHistory.occurredAt.goe(todayStart),
			)
			.fetchOne() ?: 0L

		return AdminDashboardView(
			totalUsers = totalUsers,
			todaySignups = todaySignups,
			todayActiveUsers = todayActiveUsers,
			todayCoinPurchaseAmount = todayCoinPurchaseAmount,
		)
	}
}
