package com.org.meeple.infra.coin.command.mapper

import com.org.meeple.core.coin.command.domain.CoinHistory
import com.org.meeple.infra.coin.command.entity.CoinHistoryEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun CoinHistoryEntity.toDomain(): CoinHistory =
	CoinHistory(
		id = id ?: 0,
		userId = userId,
		amount = amount,
		coinType = coinGetType,
		acquiredAt = occurredAt,
		coinUsageType = coinUsageType,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun CoinHistory.toEntity(): CoinHistoryEntity =
	CoinHistoryEntity(
		userId = userId,
		amount = amount,
		coinGetType = coinType,
		occurredAt = acquiredAt,
		coinUsageType = coinUsageType,
	).also { if (id != 0L) it.id = id }
