package com.org.oneulsogae.admin.universityverification.command.application.port.`in`

/** 어드민 학교 이미지 인증 심사(승인/반려) 유스케이스. */
interface ReviewUniversityImageVerificationUseCase {

	/** 인증을 승인(APPROVED)하고 해당 유저의 학교명을 [universityName]으로 확정한다. */
	fun approve(id: Long, universityName: String)

	/** 인증을 반려(REJECTED)하고 사유([reason], 선택)를 남긴다. */
	fun reject(id: Long, reason: String?)
}
