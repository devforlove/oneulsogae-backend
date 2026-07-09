package com.org.meeple.core.user.command.application.port.out

import java.time.LocalDateTime

/** 탈퇴 파기 시 사용자의 본인확인 기록에서 개인정보를 제거하고 소프트삭제한다. */
interface AnonymizeIdentityVerificationPort {
	fun anonymize(userId: Long, at: LocalDateTime)
}
