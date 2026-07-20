package com.org.meeple.core.payments.command.application.port.out

import com.org.meeple.core.payments.command.domain.GatheringPayment

/** 모임(좌석) 결제 기록을 저장하는 아웃포트. */
interface SaveGatheringPaymentPort {

	fun save(gatheringPayment: GatheringPayment): GatheringPayment
}
