package com.org.oneulsogae.core.lounge.command.application.port.out

/**
 * 라운지 글의 조회수(view_count) 증가 out-port.
 * 동시 조회가 겹쳐도 어긋나지 않도록 원자 UPDATE(`view_count = view_count + 1`)로 구현한다.
 */
interface IncreaseLoungePostViewCountPort {

	/** 조회수를 1 올린다. 없는 글이면 아무 행도 갱신되지 않는다. */
	fun increaseViewCount(postId: Long)
}
