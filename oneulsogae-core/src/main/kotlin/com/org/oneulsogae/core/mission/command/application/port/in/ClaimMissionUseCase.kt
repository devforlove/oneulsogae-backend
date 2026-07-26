package com.org.oneulsogae.core.mission.command.application.port.`in`

import com.org.oneulsogae.core.mission.command.application.port.`in`.result.ClaimMissionResult

/** 미션 보상 수령 인포트. 자격을 재검증하고 코인 적립 + 완료 기록을 원자적으로 처리한다. */
interface ClaimMissionUseCase {

	fun claim(userId: Long, missionId: Long): ClaimMissionResult
}
