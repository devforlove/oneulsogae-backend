package com.org.oneulsogae.core.common.time

import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 시스템 시계를 사용하는 [TimeGenerator] 기본 구현. */
@Component
class SystemTimeGenerator : TimeGenerator {

	override fun now(): LocalDateTime = LocalDateTime.now()
}
