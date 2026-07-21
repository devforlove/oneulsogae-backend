package com.org.oneulsogae.core.lounge.query.service.port.`in`

import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage

/**
 * 라운지 대화 신청 목록 조회 유스케이스.
 * 받은 신청과 보낸 신청은 성격이 다른 별개 목록이라 각자 커서를 갖는다.
 * 신청이 많아질 수 있어 커서 기반으로 한 페이지씩 내려준다.
 */
interface GetLoungeChatRequestsUseCase {

	/** [userId]가 자기 셀소로 받은 신청 한 페이지. (내가 쓴 모든 셀소 합산) */
	fun getReceived(userId: Long, cursor: Long?): LoungeChatRequestPage

	/** [userId]가 남의 셀소에 보낸 신청 한 페이지. */
	fun getSent(userId: Long, cursor: Long?): LoungeChatRequestPage
}
