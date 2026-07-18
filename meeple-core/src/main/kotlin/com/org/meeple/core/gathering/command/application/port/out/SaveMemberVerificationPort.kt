package com.org.meeple.core.gathering.command.application.port.out

import com.org.meeple.core.gathering.command.domain.MemberVerification

/** 멤버 인증(본인인증) 저장 out-port. */
interface SaveMemberVerificationPort {

	fun save(verification: MemberVerification): MemberVerification
}
