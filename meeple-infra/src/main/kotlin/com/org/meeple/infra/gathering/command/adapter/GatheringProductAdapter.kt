package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.admin.gathering.command.application.port.out.SaveGatheringProductPort
import com.org.meeple.admin.gathering.command.domain.GatheringProduct
import com.org.meeple.admin.gathering.command.domain.GatheringProducts
import com.org.meeple.infra.gathering.command.entity.GatheringProductEntity
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.GatheringProductJpaRepository
import org.springframework.stereotype.Component

/**
 * [GatheringProductEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 어드민 일정 생성의 상품 일괄 저장([SaveGatheringProductPort]) out-port를 구현한다.
 */
@Component
class GatheringProductAdapter(
	private val gatheringProductJpaRepository: GatheringProductJpaRepository,
) : SaveGatheringProductPort {

	override fun saveAll(products: GatheringProducts) {
		val entities: List<GatheringProductEntity> =
			products.values.map { product: GatheringProduct -> product.toEntity() }
		gatheringProductJpaRepository.saveAll(entities)
	}
}
