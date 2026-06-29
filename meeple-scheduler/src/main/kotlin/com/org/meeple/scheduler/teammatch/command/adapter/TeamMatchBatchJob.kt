package com.org.meeple.scheduler.teammatch.command.adapter

import com.org.meeple.scheduler.teammatch.command.application.port.`in`.RunTeamMatchBatchUseCase
import com.org.meeple.scheduler.teammatch.command.domain.TeamMatchBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 일일 팀 매칭 배치 실행 진입점. (크론 / 관리자 수동 트리거 공통)
 * [RunTeamMatchBatchUseCase]를 실행하고 시작/종료를 로깅한다. "언제 돌릴지"는 이 모듈을 의존하는 실행 앱(meeple-api)이 담당한다.
 * 크론과 수동 트리거가 모두 이 단일 진입점을 거치므로, 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class TeamMatchBatchJob(
	private val runTeamMatchBatchUseCase: RunTeamMatchBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	/** 현재 배치가 실행 중인지. (크론·수동 트리거가 겹쳐 동시에 도는 것을 막는 프로세스 내 가드) */
	private val running: AtomicBoolean = AtomicBoolean(false)

	/** 일일 팀 매칭 배치를 실행하고 결과를 반환한다. 이미 실행 중이면 건너뛰고 null을 반환한다. */
	fun run(): TeamMatchBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("일일 팀 매칭 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("일일 팀 매칭 배치 시작")
			val result: TeamMatchBatchResult = runTeamMatchBatchUseCase.run()
			log.info("일일 팀 매칭 배치 종료: {}", result)
			result
		} finally {
			running.set(false)
		}
	}
}
