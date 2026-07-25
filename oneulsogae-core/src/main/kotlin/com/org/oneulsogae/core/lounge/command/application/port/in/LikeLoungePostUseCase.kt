package com.org.oneulsogae.core.lounge.command.application.port.`in`

/** 라운지 글 좋아요 등록 유스케이스. (멱등 — 이미 눌렀으면 아무것도 하지 않는다) */
interface LikeLoungePostUseCase {

	/** [userId]가 글([postId])에 좋아요를 누른다. */
	fun like(userId: Long, postId: Long)
}
