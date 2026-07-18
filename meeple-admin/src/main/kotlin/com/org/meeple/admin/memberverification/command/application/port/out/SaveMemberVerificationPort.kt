package com.org.meeple.admin.memberverification.command.application.port.out

import com.org.meeple.admin.memberverification.command.domain.AdminMemberVerification

/** 멤버 인증 상태 변경을 저장하는 out-port. (status·rejectionReason만 반영하고 다른 필드는 보존) */
fun interface SaveMemberVerificationPort {

	fun save(verification: AdminMemberVerification): AdminMemberVerification
}
