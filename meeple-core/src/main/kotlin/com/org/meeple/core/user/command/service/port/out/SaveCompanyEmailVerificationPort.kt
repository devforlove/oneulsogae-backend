package com.org.meeple.core.user.command.service.port.out

import com.org.meeple.core.user.command.domain.CompanyEmailVerification

/**
 * 회사 이메일 인증 저장 아웃포트.
 * 신규 인증 요청을 저장하거나, 기존 요청(id 존재)의 검증 상태 변경분을 반영한다.
 */
interface SaveCompanyEmailVerificationPort {

	fun save(verification: CompanyEmailVerification): CompanyEmailVerification
}
