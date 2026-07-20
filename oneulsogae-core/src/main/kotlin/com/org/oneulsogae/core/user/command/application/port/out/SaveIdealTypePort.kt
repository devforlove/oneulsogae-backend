package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.UserIdealType

/** 이상형 저장 아웃포트. 신규 저장 또는 기존 행(id 존재) 갱신을 반영한다. */
interface SaveIdealTypePort {

	fun save(idealType: UserIdealType): UserIdealType
}
