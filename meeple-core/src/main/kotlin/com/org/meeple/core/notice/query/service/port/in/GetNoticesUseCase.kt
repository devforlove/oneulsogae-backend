package com.org.meeple.core.notice.query.service.port.`in`

import com.org.meeple.core.notice.query.dto.NoticePage

/** 공지 목록 조회 인포트(유스케이스). */
interface GetNoticesUseCase {

	/** 공지를 저장 날짜(생성 시각) 최신순으로 [page](0부터)·[size] 단위 페이징해 조회한다. */
	fun getNotices(page: Int, size: Int): NoticePage
}
