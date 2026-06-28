package com.org.meeple.infra.inquiry.command.mapper

import com.org.meeple.core.inquiry.command.domain.Inquiry
import com.org.meeple.infra.inquiry.command.entity.InquiryEntity

fun InquiryEntity.toDomain(): Inquiry =
	Inquiry(
		id = id ?: 0,
		userId = userId,
		category = category,
		email = email,
		message = message,
		status = status,
		answer = answer,
		answeredAt = answeredAt,
	)

fun Inquiry.toEntity(): InquiryEntity =
	InquiryEntity(
		userId = userId,
		category = category,
		email = email,
		message = message,
		status = status,
		answer = answer,
		answeredAt = answeredAt,
	).also { if (id != 0L) it.id = id }
