package com.org.meeple.admin.companyverification.command.application.port.out

import com.org.meeple.admin.companyverification.command.domain.AdminCompanyImageVerification

/** 인증 상태 변경을 저장하는 out-port. (status만 반영하고 다른 필드는 보존) */
fun interface SaveCompanyImageVerificationPort {

	fun save(verification: AdminCompanyImageVerification): AdminCompanyImageVerification
}
