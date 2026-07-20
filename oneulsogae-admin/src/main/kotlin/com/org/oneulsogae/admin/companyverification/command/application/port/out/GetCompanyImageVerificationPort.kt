package com.org.oneulsogae.admin.companyverification.command.application.port.out

import com.org.oneulsogae.admin.companyverification.command.domain.AdminCompanyImageVerification

/** 심사 대상 인증을 로드하는 out-port. */
fun interface GetCompanyImageVerificationPort {

	/** [id]로 인증을 조회한다. 없거나 soft-delete면 null. */
	fun findById(id: Long): AdminCompanyImageVerification?
}
