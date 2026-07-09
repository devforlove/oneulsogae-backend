package com.org.meeple.core.user.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 본인확인(KCP) 애그리거트. 거래등록 시 REQUESTED로 생성되고, 결과 확정 시 VERIFIED로 전이하며 검증값을 담는다.
 * 영속성은 [com.org.meeple.infra.user.command.entity.IdentityVerificationEntity]가 담당한다.
 */
data class IdentityVerification(
	val id: Long = 0,
	val userId: Long,
	val ordrIdxx: String,
	val regCertKey: String,
	val status: IdentityVerificationStatus,
	val realName: String? = null,
	val birthday: LocalDate? = null,
	val gender: Gender? = null,
	val phoneNumber: String? = null,
	val ci: String? = null,
	val di: String? = null,
	val foreigner: Boolean? = null,
	val telecom: String? = null,
	val verifiedAt: LocalDateTime? = null,
) {

	/** confirm 요청의 거래 정보가 저장된 거래와 일치하는지(위변조) + 미확정 상태인지 검증한다. */
	fun validateForConfirm(regCertKey: String, ordrIdxx: String) {
		if (this.regCertKey != regCertKey || this.ordrIdxx != ordrIdxx) {
			throw BusinessException(UserErrorCode.IDENTITY_VERIFICATION_MISMATCH)
		}
		if (status == IdentityVerificationStatus.VERIFIED) {
			throw BusinessException(UserErrorCode.IDENTITY_ALREADY_VERIFIED)
		}
	}

	/** 검증된 신원으로 확정한다. 성인이 아니면 예외를 던진다. */
	fun complete(certified: CertifiedIdentity, today: LocalDate, at: LocalDateTime): IdentityVerification {
		if (!certified.isAdult(today)) {
			throw BusinessException(UserErrorCode.IDENTITY_NOT_ADULT)
		}
		return copy(
			status = IdentityVerificationStatus.VERIFIED,
			realName = certified.realName,
			birthday = certified.birthday,
			gender = certified.gender,
			phoneNumber = certified.phoneNumber,
			ci = certified.ci,
			di = certified.di,
			foreigner = certified.foreigner,
			telecom = certified.telecom,
			verifiedAt = at,
		)
	}

	companion object {

		/** 거래등록 직후의 본인확인 요청을 생성한다. (status REQUESTED) */
		fun request(userId: Long, ordrIdxx: String, regCertKey: String): IdentityVerification =
			IdentityVerification(
				userId = userId,
				ordrIdxx = ordrIdxx,
				regCertKey = regCertKey,
				status = IdentityVerificationStatus.REQUESTED,
			)
	}
}
