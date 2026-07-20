package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.ConfirmIdentityVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.command.ConfirmIdentityVerificationCommand
import com.org.oneulsogae.core.user.command.application.port.`in`.result.ConfirmIdentityVerificationResult
import com.org.oneulsogae.core.user.command.application.port.out.ExistsIdentityByPhoneNumberPort
import com.org.oneulsogae.core.user.command.application.port.out.GetIdentityVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertQueryPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveIdentityVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUserDetailPort
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import com.org.oneulsogae.core.user.command.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ConfirmIdentityVerificationService(
	private val getIdentityVerificationPort: GetIdentityVerificationPort,
	private val saveIdentityVerificationPort: SaveIdentityVerificationPort,
	private val kcpCertQueryPort: KcpCertQueryPort,
	private val existsIdentityByPhoneNumberPort: ExistsIdentityByPhoneNumberPort,
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val timeGenerator: TimeGenerator,
) : ConfirmIdentityVerificationUseCase {

	@Transactional
	override fun confirm(userId: Long, command: ConfirmIdentityVerificationCommand): ConfirmIdentityVerificationResult {
		val now: LocalDateTime = timeGenerator.now()

		val verification: IdentityVerification = getIdentityVerificationPort.findLatestByUserId(userId)
			?: throw BusinessException(UserErrorCode.IDENTITY_VERIFICATION_NOT_FOUND)
		verification.validateForConfirm(command.regCertKey, command.ordrIdxx)

		val certified: CertifiedIdentity = kcpCertQueryPort.query(command.regCertKey, command.ordrIdxx)

		if (existsIdentityByPhoneNumberPort.existsVerifiedByPhoneNumberOnOtherUser(certified.phoneNumber, userId)) {
			throw BusinessException(UserErrorCode.IDENTITY_ALREADY_REGISTERED)
		}

		saveIdentityVerificationPort.save(verification.complete(certified, now.toLocalDate(), now))

		reflectToUserDetail(userId, certified)

		return ConfirmIdentityVerificationResult(
			name = certified.realName,
			adult = certified.isAdult(now.toLocalDate()),
			gender = certified.gender,
		)
	}

	/** 검증된 생년월일·성별·전화번호를 신뢰값으로 프로필에 반영한다. (프로필이 없으면 생성) */
	private fun reflectToUserDetail(userId: Long, certified: CertifiedIdentity) {
		val detail: UserDetail = getUserDetailPort.findByUserId(userId) ?: UserDetail.create(userId)
		saveUserDetailPort.save(
			detail.copy(
				birthday = certified.birthday,
				gender = certified.gender,
				phoneNumber = certified.phoneNumber,
			),
		)
	}
}
