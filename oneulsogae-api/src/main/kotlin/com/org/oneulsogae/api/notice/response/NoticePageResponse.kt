package com.org.oneulsogae.api.notice.response

import com.org.oneulsogae.core.notice.query.dto.NoticePage

/**
 * 공지 목록 페이지 응답. (limit/offset 페이징)
 * [content]는 현재 페이지의 공지 목록, [page]/[size]는 요청한 페이지 위치/크기,
 * [totalElements]는 전체 공지 개수, [totalPages]는 전체 페이지 수, [hasNext]는 다음 페이지 존재 여부다.
 */
data class NoticePageResponse(
	val content: List<NoticeResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(noticePage: NoticePage): NoticePageResponse =
			NoticePageResponse(
				content = NoticeResponse.listOf(noticePage.notices),
				page = noticePage.page,
				size = noticePage.size,
				totalElements = noticePage.totalElements,
				totalPages = noticePage.totalPages,
				hasNext = noticePage.hasNext,
			)
	}
}
