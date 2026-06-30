package com.org.meeple.core.user.command.application.port.out

import com.org.meeple.core.user.command.domain.User
import java.time.LocalDateTime

/** 소프트삭제된 사용자를 복구(deleted_at 해제)하는 아웃포트. */
interface RestoreUserPort {

	/**
	 * [userId] 사용자의 deleted_at을 해제하고 last_login_at을 [at]으로 갱신한 뒤, 복구된 도메인 모델을 반환한다.
	 * status는 탈퇴 시점의 원본(ONBOARDING·ACTIVE 등)을 그대로 보존한다.
	 */
	fun restore(userId: Long, at: LocalDateTime): User
}
