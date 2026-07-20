package com.org.oneulsogae.admin.notice.command.application.port.out

import com.org.oneulsogae.admin.notice.command.domain.AdminNotice

/** 어드민 공지 저장 out-port. infra 어댑터가 구현한다. */
fun interface SaveAdminNoticePort {
	fun save(notice: AdminNotice)
}
