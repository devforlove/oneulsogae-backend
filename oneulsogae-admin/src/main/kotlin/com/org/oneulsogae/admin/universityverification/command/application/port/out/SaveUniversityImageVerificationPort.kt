package com.org.oneulsogae.admin.universityverification.command.application.port.out

import com.org.oneulsogae.admin.universityverification.command.domain.AdminUniversityImageVerification

/** 인증 상태 변경을 저장하는 out-port. (status만 반영하고 다른 필드는 보존) */
fun interface SaveUniversityImageVerificationPort {

	fun save(verification: AdminUniversityImageVerification): AdminUniversityImageVerification
}
