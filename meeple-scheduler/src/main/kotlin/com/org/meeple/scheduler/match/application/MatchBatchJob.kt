package com.org.meeple.scheduler.match.application

import com.org.meeple.scheduler.match.application.port.`in`.RunDailyMatchBatchUseCase
import com.org.meeple.scheduler.match.domain.MatchBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 일일 매칭 배치 실행 로직.
 * [RunDailyMatchBatchUseCase]를 실행하고 시작/종료를 로깅한다.
 * "언제 돌릴지"(@Scheduled 크론 트리거)는 이 모듈을 의존하는 실행 앱(meeple-api)이 담당하고,
 * 이 클래스는 "무엇을 실행할지"(배치 실행 진입점)만 책임지는 애플리케이션 로직이다.
 * (배치 알고리즘 자체는 meeple-core의 UseCase에 있다)
 */
@Component
class MatchBatchJob(
	private val runDailyMatchBatchUseCase: RunDailyMatchBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	fun run() {
		log.info("일일 매칭 배치 시작")
		val result: MatchBatchResult = runDailyMatchBatchUseCase.run()
		log.info("일일 매칭 배치 종료: {}", result)
	}
}
