package com.org.meeple.scheduler.match.command.application.port.`in`

import com.org.meeple.scheduler.match.command.domain.TeamMatchBatchResult

/**
 * 일일 팀 매칭 배치 실행 유스케이스(in-port). 결성(ACTIVE) 팀끼리 지역 근접 기반으로 소개를 만든다.
 * 구현은 [com.org.meeple.scheduler.match.command.application.TeamMatchBatchService].
 */
interface RunTeamMatchBatchUseCase {

	fun run(): TeamMatchBatchResult
}
