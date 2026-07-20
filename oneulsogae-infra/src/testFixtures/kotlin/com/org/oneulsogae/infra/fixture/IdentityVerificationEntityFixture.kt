package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.IdentityVerificationEntity
import java.time.LocalDate
import java.time.LocalDateTime

object IdentityVerificationEntityFixture {

	fun create(
		userId: Long = 1L,
		ordrIdxx: String = "ORD-FIX",
		regCertKey: String = "REG-FIX",
		status: IdentityVerificationStatus = IdentityVerificationStatus.VERIFIED,
		realName: String? = "홍길동",
		phoneNumber: String? = "01012345678",
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
			realName = realName,
			birthday = birthday,
			gender = gender,
			phoneNumber = phoneNumber,
			di = di,
			ciEncrypted = "enc",
			foreigner = false,
			telecom = "SKT",
			verifiedAt = verifiedAt,
		)
}
