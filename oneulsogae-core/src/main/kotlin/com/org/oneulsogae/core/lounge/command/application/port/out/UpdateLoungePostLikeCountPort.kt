package com.org.oneulsogae.core.lounge.command.application.port.out

/**
 * 라운지 글의 표시용 좋아요 총합(like_count) 증감 out-port.
 * 동시 증감이 겹쳐도 어긋나지 않도록 원자 UPDATE(`like_count = like_count ± 1`)로 구현한다.
 */
interface UpdateLoungePostLikeCountPort {

	/** 좋아요 총합을 1 올린다. */
	fun increaseLikeCount(postId: Long)

	/** 좋아요 총합을 1 내린다. */
	fun decreaseLikeCount(postId: Long)
}
