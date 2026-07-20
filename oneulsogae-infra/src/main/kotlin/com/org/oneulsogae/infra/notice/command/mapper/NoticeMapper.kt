package com.org.oneulsogae.infra.notice.command.mapper

import com.org.oneulsogae.core.notice.command.domain.Notice
import com.org.oneulsogae.infra.notice.command.entity.NoticeEntity

fun NoticeEntity.toDomain(): Notice =
	Notice(
		id = id ?: 0,
		title = title,
		description = description,
	)

fun Notice.toEntity(): NoticeEntity =
	NoticeEntity(
		title = title,
		description = description,
	).also { if (id != 0L) it.id = id }
