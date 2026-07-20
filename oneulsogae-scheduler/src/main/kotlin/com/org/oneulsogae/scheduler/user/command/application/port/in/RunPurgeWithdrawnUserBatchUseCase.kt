package com.org.oneulsogae.scheduler.user.command.application.port.`in`

import com.org.oneulsogae.scheduler.user.command.domain.PurgeWithdrawnUserBatchResult

/** 탈퇴 유예 경과 사용자 파기 배치 실행 유스케이스(in-port). */
interface RunPurgeWithdrawnUserBatchUseCase {

	fun run(): PurgeWithdrawnUserBatchResult
}
