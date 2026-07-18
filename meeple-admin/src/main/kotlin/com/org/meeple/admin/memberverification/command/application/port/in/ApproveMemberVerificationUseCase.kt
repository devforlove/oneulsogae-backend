package com.org.meeple.admin.memberverification.command.application.port.`in`

/** 어드민 멤버 인증 심사(승인) 유스케이스. */
interface ApproveMemberVerificationUseCase {

	/** 멤버 인증을 승인(APPROVED)한다. 없으면 예외를 던진다. */
	fun approve(id: Long)
}
