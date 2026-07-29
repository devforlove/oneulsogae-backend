package com.org.oneulsogae.core.user.query.dao

import com.org.oneulsogae.core.user.query.dto.UserCompany

/**
 * 회사 매핑 조회 dao(query out-port 인터페이스). 이메일 도메인으로 회사를 조회하며, QueryDSL 구현은 infra가 담당한다.
 */
interface GetUserCompanyDao {

	/** 이메일 도메인(예: "oneulsogae.com")에 매핑된 회사 후보 목록을 조회한다. 한 도메인을 여러 회사가 공유할 수 있다(1:N). 없으면 빈 목록. */
	fun findAllByEmailDomain(emailDomain: String): List<UserCompany>

	/** 회사 매핑 id로 회사를 조회한다. 없으면 null. */
	fun findById(userCompanyId: Long): UserCompany?
}
