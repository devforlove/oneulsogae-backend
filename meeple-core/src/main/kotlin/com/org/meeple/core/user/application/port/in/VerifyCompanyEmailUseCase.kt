package com.org.meeple.core.user.application.port.`in`

import com.org.meeple.core.user.application.port.`in`.result.VerifyCompanyEmailResult

/**
 * 회사 이메일 인증번호 검증 인포트(유스케이스).
 * 사용자가 앱에서 입력한 인증번호를 검증하고, 성공하면 회사 이메일/회사명을 확정한 뒤 사용자를 정식 가입(ACTIVE) 처리한다.
 * 회사명 매핑 성공 여부를 [VerifyCompanyEmailResult]로 반환한다.
 * 인증번호가 일치하지 않거나 만료/이미 사용된 경우 [com.org.meeple.core.common.error.BusinessException]을 던진다.
 */
interface VerifyCompanyEmailUseCase {

	fun verify(userId: Long, code: String): VerifyCompanyEmailResult
}
