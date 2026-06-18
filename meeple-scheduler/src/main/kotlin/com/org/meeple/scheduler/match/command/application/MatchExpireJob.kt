package com.org.meeple.scheduler.match.command.application

import com.org.meeple.scheduler.match.command.application.port.`in`.ExpireMatchesUseCase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 만료된 소개 정리 배치 실행 로직.
 * [ExpireMatchesUseCase]를 실행하고 시작/종료를 로깅한다.
 * "언제 돌릴지"(@Scheduled 크론)는 이 모듈을 의존하는 실행 앱(meeple-api)이 담당하고, 이 클래스는 "무엇을 실행할지"만 책임진다.
 * 크론·수동 트리거가 겹쳐도 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class MatchExpireJob(
	private val expireMatchesUseCase: ExpireMatchesUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	/** 현재 만료 정리 배치가 실행 중인지. (트리거가 겹쳐 동시에 도는 것을 막는 프로세스 내 가드) */
	private val running: AtomicBoolean = AtomicBoolean(false)

	/** 만료 소개 정리 배치를 실행하고 제거 건수를 반환한다. 이미 실행 중이면 건너뛰고 null을 반환한다. */
	fun run(): Int? {
		if (!running.compareAndSet(false, true)) {
			log.warn("매칭 만료 정리 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("매칭 만료 정리 배치 시작")
			val removed: Int = expireMatchesUseCase.run()
			log.info("매칭 만료 정리 배치 종료: 제거={}", removed)
			removed
		} finally {
			running.set(false)
		}
	}
}
