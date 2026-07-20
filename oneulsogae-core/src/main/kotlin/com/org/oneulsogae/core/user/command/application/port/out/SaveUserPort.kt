package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.User

/**
 * 사용자 저장 아웃포트.
 * 신규 사용자를 저장하거나, 기존 사용자(id 존재)의 변경분을 반영한다.
 */
interface SaveUserPort {

	fun save(user: User): User
}
