package com.org.oneulsogae.api.notice.response

import com.org.oneulsogae.core.notice.query.dto.NoticeView
import com.org.oneulsogae.core.notice.query.dto.NoticeViews
import java.time.LocalDateTime

/**
 * 공지 응답. 공지 한 건을 담는다.
 * [createdAt]은 저장 날짜(생성 시각)다.
 */
data class NoticeResponse(
	val id: Long,
	val title: String,
	val description: String,
	val createdAt: LocalDateTime?,
) {
	companion object {
		private fun of(notice: NoticeView): NoticeResponse =
			NoticeResponse(
				id = notice.id,
				title = notice.title,
				description = notice.description,
				createdAt = notice.createdAt,
			)

		/** 공지 목록을 응답 목록으로 변환한다. (최신순 정렬은 조회 단계에서 보장된다) */
		fun listOf(notices: NoticeViews): List<NoticeResponse> =
			notices.values.map { notice: NoticeView -> of(notice) }
	}
}
