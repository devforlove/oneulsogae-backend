package com.org.meeple.infra.report.command.mapper

import com.org.meeple.core.report.command.domain.Report
import com.org.meeple.infra.report.command.entity.ReportEntity

fun ReportEntity.toDomain(): Report =
	Report(
		id = id ?: 0,
		type = type,
		fromUserId = fromUserId,
		chatRoomId = chatRoomId,
		toTeamId = toTeamId,
		toUserId = toUserId,
		description = description,
	)

fun Report.toEntity(): ReportEntity =
	ReportEntity(
		type = type,
		fromUserId = fromUserId,
		chatRoomId = chatRoomId,
		toTeamId = toTeamId,
		toUserId = toUserId,
		description = description,
	).also { if (id != 0L) it.id = id }
