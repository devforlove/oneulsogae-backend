package com.org.oneulsogae.core.lounge.query.dao

import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView

/** 받은 대화 신청 조회 dao. (조회 전용) */
interface GetLoungeChatRequestDao {

	/** 글 작성자의 사용자 id. 글이 없거나 삭제됐으면 null. (소유권 검증용) */
	fun findAuthorUserIdByPostId(postId: Long): Long?

	/**
	 * 글에 온 신청을 최신(requestId 내림차순)순으로 최대 [limit]건 조회한다.
	 * [beforeId]를 주면 그보다 과거(requestId 미만) 구간을 잇는다. (커서 페이징)
	 */
	fun findPageByPostId(postId: Long, beforeId: Long?, limit: Int): List<LoungeChatRequestView>
}
