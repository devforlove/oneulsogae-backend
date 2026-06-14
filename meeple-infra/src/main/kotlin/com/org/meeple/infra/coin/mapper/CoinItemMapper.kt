package com.org.meeple.infra.coin.mapper

import com.org.meeple.core.coin.domain.CoinItem
import com.org.meeple.infra.coin.entity.CoinItemEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun CoinItemEntity.toDomain(): CoinItem =
	CoinItem(
		id = id ?: 0,
		coinAmount = coinAmount,
		price = price,
		salePrice = salePrice,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun CoinItem.toEntity(): CoinItemEntity =
	CoinItemEntity(
		coinAmount = coinAmount,
		price = price,
		salePrice = salePrice,
	).also { if (id != 0L) it.id = id }
