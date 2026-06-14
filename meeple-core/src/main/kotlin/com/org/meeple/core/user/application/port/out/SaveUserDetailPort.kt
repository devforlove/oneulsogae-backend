package com.org.meeple.core.user.application.port.out

import com.org.meeple.core.user.domain.UserDetail

/**
 * 사용자 프로필 상세 저장 아웃포트.
 * 신규 프로필을 저장하거나, 기존 프로필(id 존재)의 변경분을 반영한다.
 */
interface SaveUserDetailPort {

	fun save(userDetail: UserDetail): UserDetail
}
