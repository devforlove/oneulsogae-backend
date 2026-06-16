package com.org.meeple.core.user.query.dto

/**
 * 회사 이메일 도메인과 회사명의 매핑 조회 결과(read model / lookup).
 * 사용자가 입력한 회사 이메일의 도메인([emailDomain])으로 어떤 회사([companyName])인지 식별한다.
 * 영속성은 [com.org.meeple.infra.user.command.entity.UserCompanyEntity]가 담당한다. (쓰기 경로가 없는 순수 조회 lookup)
 */
data class UserCompany(
	val id: Long = 0,
	val emailDomain: String,
	val companyName: String,
)
