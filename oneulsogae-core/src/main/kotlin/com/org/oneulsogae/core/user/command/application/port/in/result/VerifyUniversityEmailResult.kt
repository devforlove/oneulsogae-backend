package com.org.oneulsogae.core.user.command.application.port.`in`.result

/**
 * 학교 이메일 인증 검증 결과.
 * 검증으로 확정된 학교명([universityName])을 담는다. (등록된 학교만 인증을 통과하므로 항상 존재한다)
 */
data class VerifyUniversityEmailResult(
	val universityName: String,
)
