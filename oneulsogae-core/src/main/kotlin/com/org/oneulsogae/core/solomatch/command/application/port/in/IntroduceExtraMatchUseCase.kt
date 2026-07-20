package com.org.oneulsogae.core.solomatch.command.application.port.`in`

import com.org.oneulsogae.core.solomatch.command.domain.Match

/** 추가 소개 유스케이스. 코인 차감 후 자격 후보 1명을 골라 PROPOSED 매칭을 만든다. */
interface IntroduceExtraMatchUseCase {
	fun introduce(userId: Long): Match
}
