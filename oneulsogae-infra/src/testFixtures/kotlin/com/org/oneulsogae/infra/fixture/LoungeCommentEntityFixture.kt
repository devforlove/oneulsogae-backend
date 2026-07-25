package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.lounge.command.entity.LoungeCommentEntity

/** [LoungeCommentEntity] 테스트 픽스처. 기본은 root 댓글이다. (parentId를 주면 대댓글) */
object LoungeCommentEntityFixture {

	fun create(
		postId: Long = 1L,
		userId: Long = 1L,
		parentId: Long? = null,
		content: String = "댓글 내용",
	): LoungeCommentEntity =
		LoungeCommentEntity(
			postId = postId,
			userId = userId,
			parentId = parentId,
			content = content,
		)
}
