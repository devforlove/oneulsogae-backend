package com.org.oneulsogae.core.lounge.command.application.port.`in`.result

/** 대화 신청 결과. 생성된 신청의 id를 돌려준다. (수락 API의 키) */
data class RequestLoungeChatResult(
	val requestId: Long,
)
