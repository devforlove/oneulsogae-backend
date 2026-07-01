package com.org.meeple.core.user.command.application.port.`in`

import com.org.meeple.core.user.command.domain.UserDetail

/**
 * 현재 사용자의 보조 이메일(마케팅·광고·매칭 알림 수신용)을 설정/변경/해제하는 인포트(유스케이스).
 * null이나 공백을 넘기면 해제된다. 나머지 프로필 필드는 보존한다.
 */
interface UpdateSecondaryEmailUseCase {

	fun updateSecondaryEmail(userId: Long, secondaryEmail: String?): UserDetail
}
