package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import com.org.oneulsogae.infra.inquiry.command.entity.InquiryEntity
import java.time.LocalDateTime

/**
 * [InquiryEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 접수 시각(created_at)은 저장 시 JPA Auditing이 채운다.
 */
object InquiryEntityFixture {

	fun create(
		userId: Long? = null,
		category: InquiryCategory = InquiryCategory.ETC,
		email: String = "user@test.com",
		message: String = "문의 내용",
		status: InquiryStatus = InquiryStatus.PENDING,
		answer: String? = null,
		answeredAt: LocalDateTime? = null,
	): InquiryEntity =
		InquiryEntity(
			userId = userId,
			category = category,
			email = email,
			message = message,
			status = status,
			answer = answer,
			answeredAt = answeredAt,
		)
}
