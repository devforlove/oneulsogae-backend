package com.org.meeple.scheduler.user.command.adapter

import com.org.meeple.scheduler.user.command.application.port.`in`.RunPurgeWithdrawnUserBatchUseCase
import com.org.meeple.scheduler.user.command.domain.PurgeWithdrawnUserBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/** 탈퇴 계정 파기 배치 실행 진입점. 프로세스 내 가드로 중복 실행을 막는다. */
@Component
class PurgeWithdrawnUserBatchJob(
	private val runPurgeWithdrawnUserBatchUseCase: RunPurgeWithdrawnUserBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)
	private val running: AtomicBoolean = AtomicBoolean(false)

	fun run(): PurgeWithdrawnUserBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("탈퇴 계정 파기 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("탈퇴 계정 파기 배치 시작")
			runPurgeWithdrawnUserBatchUseCase.run()
		} finally {
			running.set(false)
		}
	}
}
