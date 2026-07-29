package com.org.oneulsogae.core.user.query.service.port.`in`

import com.org.oneulsogae.core.user.query.dto.UserCompany

/**
 * 회사 매핑 조회 인포트(유스케이스).
 * 회사 이메일의 도메인으로 매핑된 회사 후보를 조회한다. 다른 도메인은 이 인포트를 통해 회사 매핑을 참조한다.
 * (그룹 계열사처럼 한 도메인을 여러 회사가 공유할 수 있어 도메인:회사는 1:N)
 */
interface GetUserCompanyUseCase {

	/** 회사 이메일의 도메인에 매핑된 회사 후보 목록을 조회한다. 매핑이 없으면 빈 목록. */
	fun findCompaniesByEmail(companyEmail: String): List<UserCompany>

	/** 회사 매핑 id로 회사를 조회한다. 없으면 null. */
	fun findById(userCompanyId: Long): UserCompany?
}
