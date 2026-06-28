package com.org.meeple.core.notice.command.application.port.out

import com.org.meeple.core.notice.command.domain.Notice

/** 공지 저장 out-port. infra 어댑터가 구현한다. */
interface SaveNoticePort {
	fun save(notice: Notice): Notice
}
