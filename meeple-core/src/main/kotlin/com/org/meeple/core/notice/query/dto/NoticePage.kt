package com.org.meeple.core.notice.query.dto

/**
 * 공지 목록의 한 페이지(read model). limit/offset(page·size) 페이징 결과를 담는다.
 * [notices]는 현재 페이지의 공지 목록, [totalElements]는 (soft delete 제외) 전체 공지 개수다.
 * 전체 페이지 수([totalPages])와 다음 페이지 존재 여부([hasNext])는 page·size·totalElements로 계산한다.
 */
data class NoticePage(
	val notices: NoticeViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {

	/** 전체 페이지 수. (size가 0이면 0) */
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	/** 다음 페이지가 있는지 여부. */
	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {

		/** 빈 페이지. */
		fun empty(page: Int, size: Int): NoticePage =
			NoticePage(notices = NoticeViews.empty(), page = page, size = size, totalElements = 0)
	}
}
