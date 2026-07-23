package com.org.oneulsogae.scheduler.lounge.command.adapter

import com.org.oneulsogae.scheduler.lounge.command.application.port.`in`.RunExpireLoungeChatRequestBatchUseCase
import com.org.oneulsogae.scheduler.lounge.command.domain.ExpireLoungeChatRequestBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 만료 대화 신청 정리 배치 실행 진입점.
 * 크론과 수동 트리거가 모두 이 단일 진입점을 거치므로, 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class ExpireLoungeChatRequestBatchJob(
	private val runExpireLoungeChatRequestBatchUseCase: RunExpireLoungeChatRequestBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	private val running: AtomicBoolean = AtomicBoolean(false)

	/** 만료 대화 신청 정리 배치를 실행하고 결과를 반환한다. 이미 실행 중이면 건너뛰고 null을 반환한다. */
	fun run(): ExpireLoungeChatRequestBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("만료 대화 신청 정리 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("만료 대화 신청 정리 배치 시작")
			val result: ExpireLoungeChatRequestBatchResult = runExpireLoungeChatRequestBatchUseCase.run()
			log.info("만료 대화 신청 정리 배치 종료: {}", result)
			result
		} finally {
			running.set(false)
		}
	}
}
