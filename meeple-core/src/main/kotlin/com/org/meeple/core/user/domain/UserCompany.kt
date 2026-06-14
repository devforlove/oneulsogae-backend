package com.org.meeple.core.user.domain

/**
 * 회사 이메일 도메인과 회사명의 매핑 도메인 모델.
 * 사용자가 입력한 회사 이메일의 도메인([emailDomain])으로 어떤 회사([companyName])인지 식별한다.
 * 영속성은 [com.org.meeple.infra.user.entity.UserCompanyEntity]가 담당한다.
 */
data class UserCompany(
	val id: Long = 0,
	val emailDomain: String,
	val companyName: String,
)
