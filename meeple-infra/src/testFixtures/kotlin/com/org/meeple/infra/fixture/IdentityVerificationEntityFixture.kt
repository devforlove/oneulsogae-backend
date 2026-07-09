package com.org.meeple.infra.fixture

import com.org.meeple.common.user.Gender
import com.org.meeple.core.user.command.domain.IdentityVerificationStatus
import com.org.meeple.infra.user.command.entity.IdentityVerificationEntity
import java.time.LocalDate
import java.time.LocalDateTime

object IdentityVerificationEntityFixture {

	fun create(
		userId: Long = 1L,
		ordrIdxx: String = "ORD-FIX",
		regCertKey: String = "REG-FIX",
		status: IdentityVerificationStatus = IdentityVerificationStatus.VERIFIED,
		di: String? = "DI-FIX",
		birthday: LocalDate? = LocalDate.of(1996, 1, 1),
		gender: Gender? = Gender.MALE,
		verifiedAt: LocalDateTime? = LocalDateTime.of(2026, 7, 9, 12, 0),
	): IdentityVerificationEntity =
		IdentityVerificationEntity(
			userId = userId,
			ordrIdxx = ordrIdxx,
			regCertKey = regCertKey,
			status = status,
			realName = "홍길동",
			birthday = birthday,
			gender = gender,
			phoneNumber = "01012345678",
			di = di,
			ciEncrypted = "enc",
			foreigner = false,
			telecom = "SKT",
			verifiedAt = verifiedAt,
		)
}
