package com.org.oneulsogae.core.user.query.dto

/**
 * 학교 이메일 도메인과 학교명의 매핑 조회 결과(read model / lookup).
 * 사용자가 입력한 학교 이메일의 도메인([emailDomain])으로 어떤 학교([universityName])인지 식별한다.
 * 영속성은 [com.org.oneulsogae.infra.user.command.entity.UserUniversityEntity]가 담당한다. (쓰기 경로가 없는 순수 조회 lookup)
 */
data class UserUniversity(
	val id: Long = 0,
	val emailDomain: String,
	val universityName: String,
)
