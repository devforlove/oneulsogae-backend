package com.org.meeple.core.user.command.application.port.`in`

import com.org.meeple.core.user.command.application.port.`in`.command.UpdateUserDetailCommand
import com.org.meeple.core.user.command.domain.UserDetail

/**
 * 온보딩 입력값(프로필 상세 전체)을 프로필에 저장하는 인포트(유스케이스).
 * 편집 가능 필드와 회사 이메일을 교체하고, id/userId/profileImageCode/companyName은 보존한다.
 * (user 도메인의 회사 이메일 인증 플로우가 호출한다)
 */
interface UpdateUserDetailUseCase {

	fun update(userId: Long, command: UpdateUserDetailCommand): UserDetail
}
