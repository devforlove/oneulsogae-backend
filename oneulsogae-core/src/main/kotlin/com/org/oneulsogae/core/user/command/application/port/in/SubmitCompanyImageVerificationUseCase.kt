package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.command.SubmitCompanyImageVerificationCommand
import com.org.oneulsogae.core.user.command.domain.CompanyImageVerification

/** 직장 서류 이미지 인증 제출 유스케이스. */
interface SubmitCompanyImageVerificationUseCase {

	fun submit(userId: Long, command: SubmitCompanyImageVerificationCommand): CompanyImageVerification
}
