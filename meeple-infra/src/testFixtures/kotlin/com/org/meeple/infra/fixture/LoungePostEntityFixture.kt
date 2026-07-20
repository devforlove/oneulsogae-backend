package com.org.meeple.infra.fixture

import com.org.meeple.common.lounge.LoungePostType
import com.org.meeple.infra.lounge.command.entity.LoungePostEntity

/** [LoungePostEntity] 테스트 픽스처. 기본은 좋아요 0인 셀소 글이다. */
object LoungePostEntityFixture {

	fun create(
		userId: Long = 1L,
		type: LoungePostType = LoungePostType.SELF_INTRO,
		likeCount: Int = 0,
	): LoungePostEntity =
		LoungePostEntity(
			type = type,
			userId = userId,
			likeCount = likeCount,
		)
}
