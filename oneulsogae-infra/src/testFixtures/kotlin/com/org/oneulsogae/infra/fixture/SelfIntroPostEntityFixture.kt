package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.lounge.command.entity.SelfIntroPostEntity

/** [SelfIntroPostEntity] 테스트 픽스처. 본문 7개 항목을 모두 채운다. */
object SelfIntroPostEntityFixture {

	fun create(
		postId: Long,
		longDistance: String = "장거리 가능해요",
		desiredAge: String = "28~34세",
		mbti: String = "ENFP",
		marriageThought: String = "3년 안에 하고 싶어요",
		preferredPartner: String = "대화가 잘 통하는 사람",
		charmPoint: String = "잘 웃어요",
		freeWord: String = "편하게 연락 주세요",
	): SelfIntroPostEntity =
		SelfIntroPostEntity(
			postId = postId,
			longDistance = longDistance,
			desiredAge = desiredAge,
			mbti = mbti,
			marriageThought = marriageThought,
			preferredPartner = preferredPartner,
			charmPoint = charmPoint,
			freeWord = freeWord,
		)
}
