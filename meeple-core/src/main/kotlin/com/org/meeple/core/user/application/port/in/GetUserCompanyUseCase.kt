package com.org.meeple.core.user.application.port.`in`

/**
 * 회사 매핑 조회 인포트(유스케이스).
 * 회사 이메일의 도메인으로 매핑된 회사명을 조회한다. 다른 도메인은 이 인포트를 통해 회사 매핑을 참조한다.
 */
interface GetUserCompanyUseCase {

	/** 회사 이메일의 도메인으로 매핑된 회사명을 조회한다. 매핑이 없으면 null. */
	fun findCompanyNameByEmail(companyEmail: String): String?
}
