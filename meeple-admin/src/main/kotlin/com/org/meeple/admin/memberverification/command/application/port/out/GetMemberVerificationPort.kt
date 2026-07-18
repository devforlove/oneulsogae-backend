package com.org.meeple.admin.memberverification.command.application.port.out

import com.org.meeple.admin.memberverification.command.domain.AdminMemberVerification

/** 심사 대상 멤버 인증을 로드하는 out-port. */
fun interface GetMemberVerificationPort {

	/** [id]로 멤버 인증을 조회한다. 없거나 soft-delete면 null. */
	fun findById(id: Long): AdminMemberVerification?
}
