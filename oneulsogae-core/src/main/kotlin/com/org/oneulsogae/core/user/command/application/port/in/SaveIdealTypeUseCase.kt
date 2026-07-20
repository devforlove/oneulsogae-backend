package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.command.SaveIdealTypeCommand
import com.org.oneulsogae.core.user.command.domain.UserIdealType

/** 현재 사용자의 이상형을 저장(신규 생성 또는 교체)하는 인포트. */
interface SaveIdealTypeUseCase {

	fun save(userId: Long, command: SaveIdealTypeCommand): UserIdealType
}
