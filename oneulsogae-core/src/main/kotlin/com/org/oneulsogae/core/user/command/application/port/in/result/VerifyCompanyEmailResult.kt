package com.org.oneulsogae.core.user.command.application.port.`in`.result

/**
 * 회사 이메일 인증 검증 결과.
 * 검증한 회사 이메일 도메인으로 회사명을 찾았는지([isCompanyResolved])와 찾은 회사명([companyName])을 담는다.
 * (마이탭 부가 인증이라 가입 상태·코인 등 온보딩 신호는 담지 않는다)
 */
data class VerifyCompanyEmailResult(
	val companyName: String?,
) {

	/**
	 * 도메인 매핑으로 회사명을 찾았으면 true.
	 * 매핑을 못 찾으면 `VerifyCompanyEmailService.verify`가 그 자리에서 `COMPANY_NOT_FOUND`(USER-034)로 인증 자체를
	 * 실패시키므로, 이 결과가 만들어지는 시점엔 항상 true다. 프론트엔드 호환을 위해 남겨둔 필드이며 협의 후 제거 예정이다.
	 */
	val isCompanyResolved: Boolean
		get() = companyName != null
}
