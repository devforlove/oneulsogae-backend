package com.org.oneulsogae.api.home.response

/**
 * 메인 화면 진입 요약 응답.
 * 진입 시 한 번에 노출할 코인 잔액과 미수신(읽지 않은) 알람 개수를 담는다.
 */
data class HomeSummaryResponse(
	val coinBalance: Int,
	val unreadAlarmCount: Long,
)
