package com.org.meeple.infra.user.command.mapper

import com.org.meeple.core.user.command.domain.IdentityVerification
import com.org.meeple.infra.user.command.crypto.CiCipher
import com.org.meeple.infra.user.command.entity.IdentityVerificationEntity

fun IdentityVerificationEntity.toDomain(): IdentityVerification =
	IdentityVerification(
		id = id ?: 0,
		userId = userId,
		ordrIdxx = ordrIdxx,
		regCertKey = regCertKey,
		status = status,
		realName = realName,
		birthday = birthday,
		gender = gender,
		phoneNumber = phoneNumber,
		ci = null,
		di = di,
		foreigner = foreigner,
		telecom = telecom,
		verifiedAt = verifiedAt,
	)

fun IdentityVerification.toEntity(ciCipher: CiCipher): IdentityVerificationEntity =
	IdentityVerificationEntity(
		userId = userId,
		ordrIdxx = ordrIdxx,
		regCertKey = regCertKey,
		status = status,
		realName = realName,
		birthday = birthday,
		gender = gender,
		phoneNumber = phoneNumber,
		di = di,
		ciEncrypted = ci?.let { ciCipher.encrypt(it) },
		foreigner = foreigner,
		telecom = telecom,
		verifiedAt = verifiedAt,
	).also { if (id != 0L) it.id = id }
