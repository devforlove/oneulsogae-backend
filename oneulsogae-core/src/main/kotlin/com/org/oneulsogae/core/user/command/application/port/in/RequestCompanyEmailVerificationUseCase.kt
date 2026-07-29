package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.result.RequestCompanyEmailVerificationResult

/**
 * 회사 이메일 인증 요청 인포트(유스케이스).
 * 입력한 회사 이메일로 1회용 인증번호를 생성해 인증 메일을 발송한다. (온보딩과 분리된 회사 인증 플로우)
 * 같은 도메인을 쓰는 회사가 여럿이면 [userCompanyId]로 회사를 지정해야 하며, 지정 없이는 후보 목록만 돌려준다.
 */
interface RequestCompanyEmailVerificationUseCase {

	fun request(userId: Long, companyEmail: String, userCompanyId: Long?): RequestCompanyEmailVerificationResult
}
