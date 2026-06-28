package com.org.meeple.api.notice.response

import com.org.meeple.core.notice.command.domain.Notice

data class CreateNoticeResponse(
	val noticeId: Long,
) {
	companion object {
		fun of(notice: Notice): CreateNoticeResponse =
			CreateNoticeResponse(noticeId = notice.id)
	}
}
