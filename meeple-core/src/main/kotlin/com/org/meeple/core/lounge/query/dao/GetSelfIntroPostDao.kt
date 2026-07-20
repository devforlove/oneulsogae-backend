package com.org.meeple.core.lounge.query.dao

import com.org.meeple.core.lounge.query.dto.SelfIntroPostView

/** 라운지 셀소 목록 조회 dao. (조회 전용) */
interface GetSelfIntroPostDao {

	/**
	 * 셀소 글을 최신(postId 내림차순)순으로 최대 [limit]건 조회한다.
	 * [beforeId]를 주면 그보다 과거(postId 미만) 구간을 잇는다. (커서 페이징)
	 */
	fun findPage(beforeId: Long?, limit: Int): List<SelfIntroPostView>
}
