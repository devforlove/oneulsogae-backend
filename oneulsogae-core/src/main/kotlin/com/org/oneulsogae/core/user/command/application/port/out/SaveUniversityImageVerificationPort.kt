package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.UniversityImageVerification

/** 학교 서류 이미지 인증 저장 out-port. */
interface SaveUniversityImageVerificationPort {

	fun save(verification: UniversityImageVerification): UniversityImageVerification
}
