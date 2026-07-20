package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.gathering.MemberVerificationStatus
import com.org.oneulsogae.infra.gathering.command.entity.MemberVerificationEntity

/**
 * [MemberVerificationEntity] 테스트 픽스처.
 * 기본은 방금 제출돼 심사 대기(PENDING)인 상태다.
 */
object MemberVerificationEntityFixture {

	fun create(
		userId: Long = 1L,
		jobCategory: String = "IT·개발직",
		jobDetail: String = "테스트회사 백엔드 개발자",
		faceImageKey: String = "member-verifications/1/face-object.jpg",
		idCardImageKey: String = "member-verifications/1/id-card-object.jpg",
		documentImageKey: String = "member-verifications/1/document-object.pdf",
		status: MemberVerificationStatus = MemberVerificationStatus.PENDING,
		rejectionReason: String? = null,
	): MemberVerificationEntity =
		MemberVerificationEntity(
			userId = userId,
			jobCategory = jobCategory,
			jobDetail = jobDetail,
			faceImageKey = faceImageKey,
			idCardImageKey = idCardImageKey,
			documentImageKey = documentImageKey,
			status = status,
			rejectionReason = rejectionReason,
		)
}
