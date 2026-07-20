package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.CompanyImageVerificationEntity

/**
 * [CompanyImageVerificationEntity] 테스트 픽스처.
 * 기본은 방금 제출돼 심사 대기(PENDING)인 상태다.
 */
object CompanyImageVerificationEntityFixture {

	fun create(
		userId: Long = 1L,
		imageKey: String = "company-image-verifications/1/test-object.jpg",
		status: CompanyImageVerificationStatus = CompanyImageVerificationStatus.PENDING,
		companyName: String? = "테스트회사",
		previousCompanyName: String? = null,
		rejectionReason: String? = null,
	): CompanyImageVerificationEntity =
		CompanyImageVerificationEntity(
			userId = userId,
			imageKey = imageKey,
			status = status,
			companyName = companyName,
			previousCompanyName = previousCompanyName,
			rejectionReason = rejectionReason,
		)
}
