package com.org.meeple.admin.memberverification.command.application.port.`in`

/** 어드민 멤버 인증 심사(승인/반려) 유스케이스. */
interface ReviewMemberVerificationUseCase {

	/** 멤버 인증을 승인(APPROVED)한다. 없으면 예외를 던진다. */
	fun approve(id: Long)

	/** 멤버 인증을 반려(REJECTED)하고 사유([reason], 선택)를 남긴다. 없으면 예외를 던진다. */
	fun reject(id: Long, reason: String?)
}
