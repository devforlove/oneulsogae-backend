package com.org.oneulsogae.admin.notice.command.application.port.`in`

import com.org.oneulsogae.admin.notice.command.application.port.`in`.command.CreateAdminNoticeCommand

/** 어드민 공지 생성 유스케이스. */
interface CreateAdminNoticeUseCase {
	/** [command] 내용으로 공지를 생성·저장한다. */
	fun create(command: CreateAdminNoticeCommand)
}
