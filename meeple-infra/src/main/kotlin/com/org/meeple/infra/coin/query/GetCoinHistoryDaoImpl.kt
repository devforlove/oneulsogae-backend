package com.org.meeple.infra.coin.query

import com.org.meeple.core.coin.query.dao.GetCoinHistoryDao
import com.org.meeple.core.coin.query.dto.CoinHistoryView
import com.org.meeple.infra.coin.command.entity.QCoinHistoryEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetCoinHistoryDao]의 QueryDSL 구현체. (조회 전용)
 * 엔티티를 거치지 않고 [CoinHistoryView] read model로 바로 투영한다.
 * user_id 동등 + id 내림차순 keyset(`id < :beforeId`)이 `idx_user_id_id`로 받쳐져 뒤 페이지에서도 seek로 끝난다(offset 스캔 없음).
 */
@Component
class GetCoinHistoryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCoinHistoryDao {

	override fun findPageByUserId(userId: Long, beforeId: Long?, limit: Int): List<CoinHistoryView> {
		val history: QCoinHistoryEntity = QCoinHistoryEntity.coinHistoryEntity
		return queryFactory
			.select(
				Projections.constructor(
					CoinHistoryView::class.java,
					history.id,
					history.amount,
					history.coinGetType,
					history.coinUsageType,
					history.occurredAt,
				),
			)
			.from(history)
			.where(
				history.userId.eq(userId),
				beforeId?.let { cursor: Long -> history.id.lt(cursor) },
			)
			.orderBy(history.id.desc())
			.limit(limit.toLong())
			.fetch()
	}
}
