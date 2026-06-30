package com.org.meeple.core.user.command.application.port.out

import java.time.LocalDateTime

/** 파기: user_details 행의 개인정보(PII)를 제거하고 소프트삭제하는 아웃포트. */
interface AnonymizeUserDetailPort {

	/** [userId] 프로필의 PII를 모두 제거하고 deleted_at을 [at]으로 설정한다. (프로필이 없으면 무시) */
	fun anonymize(userId: Long, at: LocalDateTime)
}
