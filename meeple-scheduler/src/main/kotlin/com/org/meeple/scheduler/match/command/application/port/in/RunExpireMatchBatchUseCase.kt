package com.org.meeple.scheduler.match.command.application.port.`in`

import com.org.meeple.scheduler.match.command.domain.ExpireMatchBatchResult

/**
 * 만료 매칭 정리 배치 실행 유스케이스(in-port). 만료된(미성사) 솔로·팀 매칭을 soft-delete하고 신청자에게 절반 환불한다.
 * 구현은 [com.org.meeple.scheduler.match.command.application.ExpireMatchBatchService].
 */
interface RunExpireMatchBatchUseCase {

	fun run(): ExpireMatchBatchResult
}
