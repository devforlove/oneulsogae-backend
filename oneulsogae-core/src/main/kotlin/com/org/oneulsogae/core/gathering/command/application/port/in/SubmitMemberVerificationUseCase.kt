package com.org.oneulsogae.core.gathering.command.application.port.`in`

import com.org.oneulsogae.core.gathering.command.application.port.`in`.command.SubmitMemberVerificationCommand
import com.org.oneulsogae.core.gathering.command.domain.MemberVerification

/** 멤버 인증(본인인증) 제출 유스케이스. */
interface SubmitMemberVerificationUseCase {

	fun submit(userId: Long, command: SubmitMemberVerificationCommand): MemberVerification
}
