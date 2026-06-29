package com.org.meeple.api.user.response

import com.org.meeple.core.user.command.application.port.`in`.result.VerifyUniversityEmailResult

/**
 * 학교 이메일 인증번호 검증 결과 응답.
 * 검증으로 확정된 학교명([universityName])을 내려준다. (등록된 학교만 인증을 통과한다)
 */
data class VerifyUniversityEmailResponse(
	val universityName: String,
) {
	companion object {
		fun of(result: VerifyUniversityEmailResult): VerifyUniversityEmailResponse =
			VerifyUniversityEmailResponse(
				universityName = result.universityName,
			)
	}
}
