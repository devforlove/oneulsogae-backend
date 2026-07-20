package com.org.oneulsogae.infra.notice.command.mapper

import com.org.oneulsogae.admin.notice.command.domain.AdminNotice
import com.org.oneulsogae.infra.notice.command.entity.NoticeEntity

fun AdminNotice.toEntity(): NoticeEntity =
	NoticeEntity(
		title = title,
		description = description,
	).also { if (id != 0L) it.id = id }
