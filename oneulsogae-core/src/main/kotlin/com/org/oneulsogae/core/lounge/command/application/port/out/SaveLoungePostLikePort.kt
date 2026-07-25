package com.org.oneulsogae.core.lounge.command.application.port.out

/** 라운지 글 좋아요 행 저장 out-port. */
interface SaveLoungePostLikePort {

	/**
	 * (postId, userId) 좋아요 행이 없으면 저장하고 true, 이미 있으면 저장하지 않고 false를 반환한다.
	 * 동시 요청 경합은 유니크 제약으로 걸러진다. (충돌도 false — 멱등)
	 */
	fun saveIfAbsent(postId: Long, userId: Long): Boolean
}
