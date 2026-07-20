package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.CompanyImageVerification

/** 직장 서류 이미지 인증 저장 out-port. */
interface SaveCompanyImageVerificationPort {

	fun save(verification: CompanyImageVerification): CompanyImageVerification
}
