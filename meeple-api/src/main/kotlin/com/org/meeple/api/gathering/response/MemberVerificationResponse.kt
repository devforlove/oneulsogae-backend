package com.org.meeple.api.gathering.response

import com.org.meeple.common.gathering.MemberVerificationStatus
import com.org.meeple.core.gathering.command.domain.MemberVerification
import com.org.meeple.core.gathering.query.dto.MemberVerificationView

/**
 * 멤버 인증(본인인증) 제출/조회 응답.
 * 사진은 비공개 저장이라 파일 URL 대신 인증 식별자·심사 상태·직업 정보·반려 사유만 내려준다.
 */
data class MemberVerificationResponse(
	val verificationId: Long,
	val status: MemberVerificationStatus,
	val jobCategory: String,
	val jobDetail: String,
	val rejectionReason: String?,
) {
	companion object {

		/** 제출 결과(도메인 모델)로부터 응답을 만든다. */
		fun of(verification: MemberVerification): MemberVerificationResponse =
			MemberVerificationResponse(
				verificationId = verification.id,
				status = verification.status,
				jobCategory = verification.jobCategory,
				jobDetail = verification.jobDetail,
				rejectionReason = verification.rejectionReason,
			)

		/** 조회 결과(read model)로부터 응답을 만든다. */
		fun of(view: MemberVerificationView): MemberVerificationResponse =
			MemberVerificationResponse(
				verificationId = view.id,
				status = view.status,
				jobCategory = view.jobCategory,
				jobDetail = view.jobDetail,
				rejectionReason = view.rejectionReason,
			)
	}
}
