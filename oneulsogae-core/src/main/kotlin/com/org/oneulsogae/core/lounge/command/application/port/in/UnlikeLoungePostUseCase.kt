package com.org.oneulsogae.core.lounge.command.application.port.`in`

/** 라운지 글 좋아요 취소 유스케이스. (멱등 — 누른 적 없으면 아무것도 하지 않는다) */
interface UnlikeLoungePostUseCase {

	/** [userId]가 글([postId])에 누른 좋아요를 취소한다. */
	fun unlike(userId: Long, postId: Long)
}
