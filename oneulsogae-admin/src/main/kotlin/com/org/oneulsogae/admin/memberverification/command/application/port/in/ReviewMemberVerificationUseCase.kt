package com.org.oneulsogae.admin.memberverification.command.application.port.`in`

/** 어드민 멤버 인증 심사(승인/반려) 유스케이스. */
interface ReviewMemberVerificationUseCase {

	/**
	 * 멤버 인증을 승인(APPROVED)하고 어드민이 확정한 [companyName]·[jobCategory]·[jobDetail]을 유저 프로필에 반영한다.
	 * (회사명은 매칭 읽기 모델에도 함께 반영한다) 없으면 예외를 던진다.
	 */
	fun approve(id: Long, companyName: String, jobCategory: String, jobDetail: String)

	/** 멤버 인증을 반려(REJECTED)하고 사유([reason], 선택)를 남긴다. 없으면 예외를 던진다. */
	fun reject(id: Long, reason: String?)
}
