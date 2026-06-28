package com.org.meeple.core.user.command.application.port.`in`.result

/**
 * 회사 이메일 인증 검증 결과.
 * 검증한 회사 이메일 도메인으로 회사명을 찾았는지([isCompanyResolved])와 찾은 회사명([companyName]),
 * 이번 호출로 온보딩이 막 완료됐는지([justOnboarded])와 그때 지급되는 가입 축하 코인 수량([rewardCoin])을 담는다.
 */
data class VerifyCompanyEmailResult(
	val companyName: String?,
	val justOnboarded: Boolean,
	val rewardCoin: Int,
) {

	/** 도메인 매핑으로 회사명을 찾았으면 true. (못 찾으면 false) */
	val isCompanyResolved: Boolean
		get() = companyName != null
}
