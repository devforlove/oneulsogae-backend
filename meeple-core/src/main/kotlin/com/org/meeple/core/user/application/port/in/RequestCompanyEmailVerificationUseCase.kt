package com.org.meeple.core.user.application.port.`in`

import com.org.meeple.core.user.application.port.`in`.command.UpdateUserDetailCommand
import com.org.meeple.core.user.domain.CompanyEmailVerification

/**
 * 회사 이메일 인증 요청 인포트(유스케이스).
 * 온보딩 입력값(프로필 상세)을 저장하고, 입력한 회사 이메일로 1회용 인증 토큰을 생성해 인증 메일을 발송한다.
 */
interface RequestCompanyEmailVerificationUseCase {

	fun request(userId: Long, command: UpdateUserDetailCommand): CompanyEmailVerification
}
