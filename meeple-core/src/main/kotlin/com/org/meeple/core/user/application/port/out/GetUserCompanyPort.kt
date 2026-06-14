package com.org.meeple.core.user.application.port.out

import com.org.meeple.core.user.domain.UserCompany

/**
 * 회사 매핑 조회 아웃포트.
 * 이메일 도메인으로 회사를 조회하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetUserCompanyPort {

	/** 이메일 도메인(예: "meeple.com")으로 회사를 조회한다. 없으면 null. */
	fun findByEmailDomain(emailDomain: String): UserCompany?
}
