package com.org.oneulsogae.core.user.query.dao

import com.org.oneulsogae.core.user.query.dto.UserUniversity

/**
 * 학교 매핑 조회 dao(query out-port 인터페이스). 이메일 도메인으로 학교를 조회하며, QueryDSL 구현은 infra가 담당한다.
 */
interface GetUserUniversityDao {

	/** 이메일 도메인(예: "snu.ac.kr")으로 학교를 조회한다. 없으면 null. */
	fun findByEmailDomain(emailDomain: String): UserUniversity?
}
