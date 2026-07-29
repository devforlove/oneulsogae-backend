package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.lounge.command.entity.SelfIntroPostEntity

/** [SelfIntroPostEntity] 테스트 픽스처. 본문 항목을 모두 채운다. (mbti는 프로필 스냅샷 — null 허용) */
object SelfIntroPostEntityFixture {

	fun create(
		postId: Long,
		mbti: String? = "ENFP",
		interests: String = "러닝과 전시 관람",
		personality: String = "긍정적이고 다정해요",
		idealType: String = "대화가 잘 통하는 사람",
		charmPoint: String = "잘 웃어요",
		freeWord: String = "편하게 연락주세요",
	): SelfIntroPostEntity =
		SelfIntroPostEntity(
			postId = postId,
			mbti = mbti,
			interests = interests,
			personality = personality,
			idealType = idealType,
			charmPoint = charmPoint,
			freeWord = freeWord,
		)
}
