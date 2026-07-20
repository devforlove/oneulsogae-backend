package com.org.oneulsogae.core.payments.command.application.port.`in`

import com.org.oneulsogae.core.payments.command.application.port.`in`.command.CompleteCoinPurchaseCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompleteCoinPurchaseResult

/**
 * 코인 구매 결제완료 접수 인포트(유스케이스). PG 승인을 거쳐, 성공 시 구매한 코인을 즉시 잔액에 적립하고 결제 기록을 남긴다.
 * 모임 좌석 결제와 달리 좌석 확보·운영자 승인 단계가 없어, 승인 성공 즉시 코인이 지급된다.
 */
interface CompleteCoinPurchaseUseCase {

	fun complete(userId: Long, command: CompleteCoinPurchaseCommand): CompleteCoinPurchaseResult
}
