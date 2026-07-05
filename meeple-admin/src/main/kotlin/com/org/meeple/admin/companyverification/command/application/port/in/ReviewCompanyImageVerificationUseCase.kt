package com.org.meeple.admin.companyverification.command.application.port.`in`

/** 어드민 회사 이미지 인증 심사(승인/반려) 유스케이스. */
interface ReviewCompanyImageVerificationUseCase {

	/** 인증을 승인(APPROVED)하고 해당 유저의 회사명을 [companyName]으로 확정한다. */
	fun approve(id: Long, companyName: String)

	/** 인증을 반려(REJECTED)한다. */
	fun reject(id: Long)
}
