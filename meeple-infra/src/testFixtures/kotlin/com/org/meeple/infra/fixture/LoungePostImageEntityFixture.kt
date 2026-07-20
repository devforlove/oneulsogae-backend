package com.org.meeple.infra.fixture

import com.org.meeple.infra.lounge.command.entity.LoungePostImageEntity

/** [LoungePostImageEntity] 테스트 픽스처. 기본은 대표 사진(노출 순서 0번)이다. */
object LoungePostImageEntityFixture {

	fun create(
		postId: Long,
		imageKey: String = "lounge-posts/1/photo.jpg",
		displayOrder: Int = 0,
	): LoungePostImageEntity =
		LoungePostImageEntity(
			postId = postId,
			imageKey = imageKey,
			displayOrder = displayOrder,
		)
}
