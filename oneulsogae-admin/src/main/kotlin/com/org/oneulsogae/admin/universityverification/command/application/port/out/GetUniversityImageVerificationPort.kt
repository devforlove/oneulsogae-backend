package com.org.oneulsogae.admin.universityverification.command.application.port.out

import com.org.oneulsogae.admin.universityverification.command.domain.AdminUniversityImageVerification

/** 심사 대상 인증을 로드하는 out-port. */
fun interface GetUniversityImageVerificationPort {

	/** [id]로 인증을 조회한다. 없거나 soft-delete면 null. */
	fun findById(id: Long): AdminUniversityImageVerification?
}
