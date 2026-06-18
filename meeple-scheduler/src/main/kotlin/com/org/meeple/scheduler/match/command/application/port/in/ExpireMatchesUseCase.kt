package com.org.meeple.scheduler.match.command.application.port.`in`

/** 만료된(미응답으로 끝난) 소개를 정리하는 배치 인포트(유스케이스). */
interface ExpireMatchesUseCase {

	/** 만료된 소개를 제거하고, 제거한 매칭 수를 반환한다. */
	fun run(): Int
}
