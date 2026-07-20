package com.org.meeple.core.lounge.query.service.port.`in`

import com.org.meeple.core.lounge.query.dto.SelfIntroPostPage

/**
 * 라운지 셀소 목록 조회 유스케이스.
 * 피드가 길어질 수 있어 커서 기반으로 한 페이지씩 내려준다.
 */
interface GetSelfIntroPostsUseCase {

	/** 셀소 목록 한 페이지를 최신순으로 조회한다. [cursor]를 주면 그보다 과거 구간을 잇는다. */
	fun getPosts(cursor: Long?): SelfIntroPostPage
}
