package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest

/** 라운지 대화 신청 저장 out-port. (신규 저장과 상태 전이 저장을 함께 담당) */
interface SaveLoungeChatRequestPort {

	fun save(request: LoungeChatRequest): LoungeChatRequest
}
