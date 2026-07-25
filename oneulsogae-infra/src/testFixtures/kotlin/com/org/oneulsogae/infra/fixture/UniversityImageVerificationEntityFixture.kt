package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.UniversityImageVerificationEntity

/**
 * [UniversityImageVerificationEntity] 테스트 픽스처.
 * 기본은 방금 제출돼 심사 대기(PENDING)인 상태다.
 */
object UniversityImageVerificationEntityFixture {

	fun create(
		userId: Long = 1L,
		imageKey: String = "university-image-verifications/1/test-object.jpg",
		status: UniversityImageVerificationStatus = UniversityImageVerificationStatus.PENDING,
		universityName: String? = "테스트대학교",
		previousUniversityName: String? = null,
		rejectionReason: String? = null,
	): UniversityImageVerificationEntity =
		UniversityImageVerificationEntity(
			userId = userId,
			imageKey = imageKey,
			status = status,
			universityName = universityName,
			previousUniversityName = previousUniversityName,
			rejectionReason = rejectionReason,
		)
}
