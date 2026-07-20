package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.gathering.command.entity.GatheringProfileEntity
import java.time.LocalDate

/**
 * [GatheringProfileEntity] 테스트 픽스처. 멤버 인증 승인으로 생성되는 모임 프로필(직종·직장상세·생일·키).
 */
object GatheringProfileEntityFixture {

	fun create(
		userId: Long = 1L,
		jobCategory: String = "IT·개발직",
		jobDetail: String = "테스트회사 백엔드 개발자",
		birthday: LocalDate? = LocalDate.of(1996, 1, 1),
		height: Int? = 175,
		profileImageCode: String? = "img_01",
	): GatheringProfileEntity =
		GatheringProfileEntity(
			userId = userId,
			jobCategory = jobCategory,
			jobDetail = jobDetail,
			birthday = birthday,
			height = height,
			profileImageCode = profileImageCode,
		)
}
