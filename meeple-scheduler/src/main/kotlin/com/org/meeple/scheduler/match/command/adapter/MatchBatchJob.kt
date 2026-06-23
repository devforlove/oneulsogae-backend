package com.org.meeple.scheduler.match.command.adapter

import com.org.meeple.scheduler.match.command.application.port.`in`.RunDailyMatchBatchUseCase
import com.org.meeple.scheduler.match.command.domain.MatchBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 일일 매칭 배치 실행 로직.
 * [RunDailyMatchBatchUseCase]를 실행하고 시작/종료를 로깅한다.
 * "언제 돌릴지"(@Scheduled 크론 트리거 / 관리자 수동 트리거)는 이 모듈을 의존하는 실행 앱(meeple-api)이 담당하고,
 * 이 클래스는 "무엇을 실행할지"(배치 실행 진입점)만 책임지는 애플리케이션 로직이다.
 * (배치 알고리즘 자체는 meeple-core의 UseCase에 있다)
 *
 * 크론과 수동 트리거가 모두 이 단일 진입점을 거치므로, 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class MatchBatchJob(
	private val runDailyMatchBatchUseCase: RunDailyMatchBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	/** 현재 배치가 실행 중인지. (크론·수동 트리거가 겹쳐 동시에 도는 것을 막는 프로세스 내 가드) */
	private val running: AtomicBoolean = AtomicBoolean(false)

	/**
	 * 일일 매칭 배치를 실행하고 결과를 반환한다. 이미 실행 중이면 건너뛰고 null을 반환한다.
	 * (크론은 null을 무시해 조용히 넘어가고, 수동 트리거는 null을 "이미 실행 중"으로 처리한다)
	 */
	fun run(): MatchBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("일일 매칭 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("일일 매칭 배치 시작")
			val result: MatchBatchResult = runDailyMatchBatchUseCase.run()
			log.info("일일 매칭 배치 종료: {}", result)
			result
		} finally {
			running.set(false)
		}
	}
}
