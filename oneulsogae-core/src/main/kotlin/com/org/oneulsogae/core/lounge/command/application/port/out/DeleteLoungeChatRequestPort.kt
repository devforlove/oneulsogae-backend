package com.org.oneulsogae.core.lounge.command.application.port.out

import java.time.LocalDateTime

/** 라운지 대화 신청 soft-delete 아웃포트. (만료 정리 전용 — 삭제 후 조회·목록에서 제외된다) */
interface DeleteLoungeChatRequestPort {

	/** 대화 신청([requestId])을 [now] 시각으로 soft-delete한다. */
	fun delete(requestId: Long, now: LocalDateTime)
}
