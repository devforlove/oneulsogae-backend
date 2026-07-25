package com.org.oneulsogae.core.lounge.command.application.port.out

/** 라운지 글 좋아요 행 삭제 out-port. (좋아요 취소는 soft delete가 아니라 행 삭제다) */
interface DeleteLoungePostLikePort {

	/** (postId, userId) 좋아요 행을 지우고, 실제로 지운 행이 있으면 true를 반환한다. (없었으면 false — 멱등) */
	fun delete(postId: Long, userId: Long): Boolean
}
