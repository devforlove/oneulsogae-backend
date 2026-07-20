package com.org.oneulsogae.admin.common.time

import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 시스템 시계를 사용하는 [TimeGenerator] 기본 구현. (인프라 의존이 없어 admin 모듈에서 직접 제공한다)
 * core·scheduler·chatting의 동명 System*TimeGenerator와 빈 이름이 겹치지 않도록 클래스명을 구분한다.
 */
@Component
class SystemAdminTimeGenerator : TimeGenerator {

	override fun now(): LocalDateTime = LocalDateTime.now()
}
