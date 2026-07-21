package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.core.user.command.application.port.`in`.result.VerifyCompanyEmailResult

/**
 * 회사 이메일 인증번호 검증 결과 응답.
 * 회사명 매핑 성공 여부([isCompanyResolved])와 찾은 회사명([companyName])을 내려준다.
 * (마이탭 부가 인증이라 가입 상태를 바꾸지 않으며, 온보딩 신호는 내려주지 않는다)
 * [isCompanyResolved]는 매핑을 못 찾으면 인증 자체가 400 `USER-034`로 실패하므로 이 응답이 내려가는 한 항상 true다.
 * 프론트엔드 호환을 위해 남겨둔 필드이며, 협의 후 제거 예정이다.
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
