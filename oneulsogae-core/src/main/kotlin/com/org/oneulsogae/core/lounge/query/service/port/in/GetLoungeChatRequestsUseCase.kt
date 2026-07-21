package com.org.oneulsogae.core.lounge.query.service.port.`in`

import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage

/**
 * 내 셀소에 온 대화 신청 목록 조회 유스케이스.
 * 신청이 많아질 수 있어 커서 기반으로 한 페이지씩 내려준다.
 */
interface GetLoungeChatRequestsUseCase {

	/** [userId]가 작성한 글([postId])에 온 신청 한 페이지. 남의 글이면 거절한다. */
	fun getRequests(userId: Long, postId: Long, cursor: Long?): LoungeChatRequestPage
}
