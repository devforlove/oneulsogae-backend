package com.org.oneulsogae.scheduler.lounge.command.application.port.`in`

import com.org.oneulsogae.scheduler.lounge.command.domain.ExpireLoungeChatRequestBatchResult

/**
 * 만료 대화 신청 정리 배치 실행 유스케이스(in-port). 만료된(미수락) 라운지 대화 신청을 soft-delete하고 신청자에게 절반 환불한다.
 * 구현은 [com.org.oneulsogae.scheduler.lounge.command.application.ExpireLoungeChatRequestBatchService].
 */
interface RunExpireLoungeChatRequestBatchUseCase {

	fun run(): ExpireLoungeChatRequestBatchResult
}
