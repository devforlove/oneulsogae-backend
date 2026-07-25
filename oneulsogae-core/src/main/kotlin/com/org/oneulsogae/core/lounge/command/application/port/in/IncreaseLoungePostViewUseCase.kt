package com.org.oneulsogae.core.lounge.command.application.port.`in`

/**
 * 라운지 글 조회수 증가 유스케이스.
 * 상세 조회는 CQS를 지키기 위해 조회 서비스에서 증가시키지 않고, 컨트롤러가 조회 전에 이 명령을 따로 호출한다.
 */
interface IncreaseLoungePostViewUseCase {

	/** 글([postId])의 조회수를 1 올린다. 없는 글이면 아무것도 하지 않는다. */
	fun increase(postId: Long)
}
