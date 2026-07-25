package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.command.SubmitUniversityImageVerificationCommand
import com.org.oneulsogae.core.user.command.domain.UniversityImageVerification

/** 학교 서류 이미지 인증 제출 유스케이스. */
interface SubmitUniversityImageVerificationUseCase {

	fun submit(userId: Long, command: SubmitUniversityImageVerificationCommand): UniversityImageVerification
}
