package com.org.meeple.core.user.command.service.port.`in`

import com.org.meeple.core.user.command.service.port.`in`.command.UpdateProfileCommand
import com.org.meeple.core.user.command.domain.UserDetail

/**
 * 현재 사용자의 프로필을 수정하는 인포트(유스케이스).
 * 명령에 담긴 편집 가능 필드만 교체하며, 나이/성별/키/휴대폰번호/회사이메일은 보존한다.
 */
interface UpdateProfileUseCase {

	fun updateProfile(userId: Long, command: UpdateProfileCommand): UserDetail
}
