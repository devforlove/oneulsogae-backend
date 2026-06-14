package com.org.meeple.core.user.application.port.`in`

import com.org.meeple.core.user.domain.User

/**
 * id로 사용자를 조회하는 인포트(유스케이스).
 */
interface GetUserByIdUseCase {

	fun getById(id: Long): User
}
