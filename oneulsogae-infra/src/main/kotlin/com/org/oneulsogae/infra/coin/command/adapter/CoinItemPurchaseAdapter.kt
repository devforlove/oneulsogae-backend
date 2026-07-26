package com.org.oneulsogae.infra.coin.command.adapter

import com.org.oneulsogae.core.coin.command.application.port.out.SaveCoinItemPurchasePort
import com.org.oneulsogae.infra.coin.command.entity.CoinItemPurchaseEntity
import com.org.oneulsogae.infra.coin.command.repository.CoinItemPurchaseJpaRepository
import org.springframework.stereotype.Component

/**
 * [CoinItemPurchaseEntity] command 영속성 어댑터. 구매 가드 저장([SaveCoinItemPurchasePort])을 구현한다.
 * (user_id, item_id) 유니크 위반은 saveAndFlush 시점에 DataIntegrityViolationException으로 즉시 표면화한다.
 */
@Component
class CoinItemPurchaseAdapter(
	private val coinItemPurchaseJpaRepository: CoinItemPurchaseJpaRepository,
) : SaveCoinItemPurchasePort {

	override fun save(userId: Long, itemId: Long) {
		// saveAndFlush로 유니크 위반을 이 트랜잭션 안에서 즉시 던지게 한다(호출 서비스가 잡아 409 매핑).
		coinItemPurchaseJpaRepository.saveAndFlush(CoinItemPurchaseEntity(userId = userId, itemId = itemId))
	}
}
