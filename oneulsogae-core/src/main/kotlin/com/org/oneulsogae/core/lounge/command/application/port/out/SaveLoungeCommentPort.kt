package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungeComment

/** 라운지 댓글 저장 out-port. */
interface SaveLoungeCommentPort {

	/** 댓글을 저장(신규 INSERT 또는 기존 행 UPDATE)하고 저장된 모델을 반환한다. */
	fun save(comment: LoungeComment): LoungeComment
}
