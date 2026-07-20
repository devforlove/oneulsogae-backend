package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.admin.gathering.command.domain.GatheringProducts

/** 일정 상품 일괄 저장 out-port. infra 어댑터가 구현한다. */
fun interface SaveGatheringProductPort {

	fun saveAll(products: GatheringProducts)
}
