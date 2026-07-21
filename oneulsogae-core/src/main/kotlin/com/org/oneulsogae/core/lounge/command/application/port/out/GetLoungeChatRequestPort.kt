package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest

/** 라운지 대화 신청 조회 out-port. */
interface GetLoungeChatRequestPort {

	/** 이 사용자가 이 글에 이미 신청했는지 여부. (중복 신청 차단용) */
	fun existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean

	/** 신청 한 건. 없거나 삭제됐으면 null. */
	fun findById(requestId: Long): LoungeChatRequest?
}
