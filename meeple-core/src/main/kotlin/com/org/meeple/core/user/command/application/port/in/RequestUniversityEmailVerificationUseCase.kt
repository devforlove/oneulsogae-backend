package com.org.meeple.core.user.command.application.port.`in`

import com.org.meeple.core.user.command.domain.UniversityEmailVerification

/**
 * 학교 이메일 인증 요청 인포트(유스케이스).
 * 입력한 학교 이메일로 1회용 인증번호를 생성해 인증 메일을 발송한다. (온보딩과 무관한 선택적 추가 인증)
 */
interface RequestUniversityEmailVerificationUseCase {

	fun request(userId: Long, universityEmail: String): UniversityEmailVerification
}
