package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungePost

/** 라운지 글(공통 골격) 단건 조회 out-port. 대화 신청·수락에서 글 존재와 작성자를 확인하는 데 쓴다. */
interface GetLoungePostPort {

	/** 글 한 건. 없거나 삭제됐으면 null. */
	fun findById(postId: Long): LoungePost?
}
