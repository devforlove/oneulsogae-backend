package com.org.meeple.scheduler.match.command.adapter

import com.org.meeple.scheduler.match.command.application.port.`in`.RunRecommendTeamBatchUseCase
import com.org.meeple.scheduler.match.command.domain.RecommendTeamBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 팀 추천 일일 배치 실행 진입점. (크론 / 관리자 수동 트리거 공통)
 * [RunRecommendTeamBatchUseCase]를 실행하고 시작/종료를 로깅한다. 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class RecommendTeamBatchJob(
	private val runRecommendTeamBatchUseCase: RunRecommendTeamBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	/** 현재 배치 실행 중 여부. (크론·수동 트리거 동시 실행 방지) */
	private val running: AtomicBoolean = AtomicBoolean(false)

	/** 배치를 실행하고 결과를 반환한다. 이미 실행 중이면 null. */
	fun run(): RecommendTeamBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("팀 추천 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("팀 추천 배치 시작")
			val result: RecommendTeamBatchResult = runRecommendTeamBatchUseCase.run()
			log.info("팀 추천 배치 종료: {}", result)
			result
		} finally {
			running.set(false)
		}
	}
}
