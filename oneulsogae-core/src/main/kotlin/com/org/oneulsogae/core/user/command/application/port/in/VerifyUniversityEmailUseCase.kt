package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.result.VerifyUniversityEmailResult

/**
 * 학교 이메일 인증번호 검증 인포트(유스케이스).
 * 사용자가 앱에서 입력한 인증번호를 검증하고, 성공하면 학교 이메일/학교명을 프로필·매칭 읽기 모델에 기록한다.
 * 학교명 매핑 성공 여부를 [VerifyUniversityEmailResult]로 반환한다. (가입 상태는 바꾸지 않는다)
 * 인증번호가 일치하지 않거나 만료/이미 사용된 경우 [com.org.oneulsogae.core.common.error.BusinessException]을 던진다.
 */
interface VerifyUniversityEmailUseCase {

	fun verify(userId: Long, code: String): VerifyUniversityEmailResult
}
