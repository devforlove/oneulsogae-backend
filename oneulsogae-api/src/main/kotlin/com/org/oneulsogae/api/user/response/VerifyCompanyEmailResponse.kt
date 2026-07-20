package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.core.user.command.application.port.`in`.result.VerifyCompanyEmailResult

/**
 * 회사 이메일 인증번호 검증 결과 응답.
 * 회사명 매핑 성공 여부([isCompanyResolved])와 찾은 회사명([companyName])을 내려준다.
 * (마이탭 부가 인증이라 가입 상태를 바꾸지 않으며, 온보딩 신호는 내려주지 않는다)
 */
data class VerifyCompanyEmailResponse(
	val isCompanyResolved: Boolean,
	val companyName: String?,
) {
	companion object {
		fun of(result: VerifyCompanyEmailResult): VerifyCompanyEmailResponse =
			VerifyCompanyEmailResponse(
				isCompanyResolved = result.isCompanyResolved,
				companyName = result.companyName,
			)
	}
}
