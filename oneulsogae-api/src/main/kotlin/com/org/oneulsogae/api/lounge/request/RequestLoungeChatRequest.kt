package com.org.oneulsogae.api.lounge.request

/** 라운지 대화 신청 요청. [message]는 작성자에게 남길 선택 메시지(최대 200자)다. 본문 자체를 생략해도 된다. */
data class RequestLoungeChatRequest(
	val message: String? = null,
)
