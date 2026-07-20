package com.org.oneulsogae.core.notice.command.application.port.`in`

import com.org.oneulsogae.core.notice.command.application.port.`in`.command.CreateNoticeCommand
import com.org.oneulsogae.core.notice.command.domain.Notice

interface CreateNoticeUseCase {
	/** [command] 내용으로 공지를 생성하고, 저장된 공지를 반환한다. */
	fun create(command: CreateNoticeCommand): Notice
}
