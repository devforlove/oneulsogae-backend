package com.org.oneulsogae.infra.report.command.mapper

import com.org.oneulsogae.core.report.command.domain.Report
import com.org.oneulsogae.infra.report.command.entity.ReportEntity

fun ReportEntity.toDomain(): Report =
	Report(
		id = id ?: 0,
		type = type,
		fromUserId = fromUserId,
		chatRoomId = chatRoomId,
		toTeamId = toTeamId,
		toUserId = toUserId,
		description = description,
		status = status,
	)

fun Report.toEntity(): ReportEntity =
	ReportEntity(
		type = type,
		fromUserId = fromUserId,
		chatRoomId = chatRoomId,
		toTeamId = toTeamId,
		toUserId = toUserId,
		description = description,
		status = status,
	).also { if (id != 0L) it.id = id }
