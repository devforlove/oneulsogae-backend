package com.org.meeple.core.user.query.dao

import com.org.meeple.core.user.query.dto.UserCompany

/**
 * 회사 매핑 조회 dao(query out-port 인터페이스). 이메일 도메인으로 회사를 조회하며, QueryDSL 구현은 infra가 담당한다.
 */
interface UserCompanyDao {

	/** 이메일 도메인(예: "meeple.com")으로 회사를 조회한다. 없으면 null. */
	fun findByEmailDomain(emailDomain: String): UserCompany?
}
