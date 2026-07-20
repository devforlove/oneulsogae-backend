package com.org.oneulsogae.infra.coin.command.mapper

import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.infra.coin.command.entity.CoinBalanceEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun CoinBalanceEntity.toDomain(): CoinBalance =
	CoinBalance(
		id = id ?: 0,
		userId = userId,
		balance = balance,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun CoinBalance.toEntity(): CoinBalanceEntity =
	CoinBalanceEntity(
		userId = userId,
		balance = balance,
	).also { if (id != 0L) it.id = id }
