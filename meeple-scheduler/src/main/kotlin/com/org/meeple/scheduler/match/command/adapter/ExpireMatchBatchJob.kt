package com.org.meeple.scheduler.match.command.adapter

import com.org.meeple.scheduler.match.command.application.port.`in`.RunExpireMatchBatchUseCase
import com.org.meeple.scheduler.match.command.domain.ExpireMatchBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 만료 매칭 정리 배치 실행 진입점. (크론 / 관리자 수동 트리거 공통)
 * 크론과 수동 트리거가 모두 이 단일 진입점을 거치므로, 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class ExpireMatchBatchJob(
	private val runExpireMatchBatchUseCase: RunExpireMatchBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	private val running: AtomicBoolean = AtomicBoolean(false)

	/** 만료 매칭 정리 배치를 실행하고 결과를 반환한다. 이미 실행 중이면 건너뛰고 null을 반환한다. */
	fun run(): ExpireMatchBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("만료 매칭 정리 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("만료 매칭 정리 배치 시작")
			val result: ExpireMatchBatchResult = runExpireMatchBatchUseCase.run()
			log.info("만료 매칭 정리 배치 종료: {}", result)
			result
		} finally {
			running.set(false)
		}
	}
}
